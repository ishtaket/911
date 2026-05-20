"""Reference HTTP bridge for the PCA Android app.

The Android device cannot run `codex` or Gemini CLIs natively (spec §6.4).
This tiny server fronts whichever CLI you have installed on a paired machine
(Termux on the phone itself, a home server, or a laptop on the same Wi-Fi)
and exposes a single endpoint that the app POSTs to.

It is written against the Python STANDARD LIBRARY ONLY (http.server, json,
subprocess) — no FastAPI / uvicorn / pydantic. Those pull Rust-compiled
wheels (pydantic-core, watchfiles) that have no aarch64-android build and
fail to compile under Termux. Stdlib-only means `python server.py` just
works after a bare `pkg install python`.

BILLING MODEL (spec §6.4): CLIs run under the user's SUBSCRIPTION, not via
pay-per-token API keys.
  - codex CLI  → ChatGPT Plus/Pro,    logged in via `codex login`
  - claude CLI → Claude Pro/Max,      logged in via `/login` in REPL
  - gemini CLI → Google AI/AI Studio, logged in via first-run OAuth
This bridge invokes the CLI as a subprocess; the CLI handles its own auth
state in its config dir (~/.codex/, ~/.claude/, ~/.gemini/). No API key
ever appears in this file or in any env var.

Wire contract (matches `com.pca.assistant.llm.contract.LlmRequest`):

  POST /decide
  body: {
    "system_prompt": "...",
    "l3_profile": "...",
    "l2_day": "...",
    "l1_hour": "...",
    "previous_decision": {...} | null,
    "open_threads": [...],
    "window": {...},
    "instruction": "...",
    "reply_language": "en|ru|iw"
  }

Returns the JSON object described in spec §5 — the exact shape of LlmDecision.

Run:
  python server.py --provider codex
  # or --provider claude / --provider gemini

Then point the Android app at http://<host>:8765/ in Settings → LLM provider →
HTTP bridge.

NOTE: Defaults to loopback (127.0.0.1). Binding 0.0.0.0 exposes an unauth'd
endpoint to the whole LAN — only do that behind a reverse proxy or SSH tunnel.
The client wire format never contains raw PII (the anonymiser runs on-device
per spec §3.5).
"""
from __future__ import annotations

import argparse
import hmac
import json
import logging
import os
import secrets
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

log = logging.getLogger("pca-bridge")

# Set from argparse in __main__.
PROVIDER = "codex"

# Shared-secret auth (PCA-N-1). On Android the loopback interface is shared
# across every installed app, so any app with INTERNET permission can reach
# 127.0.0.1:<port> while the bridge runs. We require a token on /decide so a
# co-installed app can't drive the bridge (burn the user's subscription, or
# feed a crafted prompt to the CLI). The token is generated on first run,
# stored next to this script (Termux-private), and printed for the user to
# paste into the PCA app → Settings → Bridge token.
TOKEN = ""
DEFAULT_TOKEN_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pca_token")

# A single request can carry a long transcript + rollups; cap it so a rogue
# client can't OOM the bridge by streaming an unbounded body.
MAX_BODY_BYTES = 4 * 1024 * 1024


def load_or_create_token(path: str) -> str:
    """Return the persisted token, generating one (0600) on first run."""
    try:
        with open(path, encoding="utf-8") as f:
            existing = f.read().strip()
        if existing:
            return existing
    except FileNotFoundError:
        pass
    token = secrets.token_hex(32)
    with open(path, "w", encoding="utf-8") as f:
        f.write(token)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass
    return token


class BridgeError(Exception):
    """Carries an HTTP status + detail for the handler to serialise."""

    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def build_prompt(req: dict) -> str:
    """Compose a single text prompt for the CLI to consume on stdin/args."""
    window = req.get("window") or {}
    open_threads = req.get("open_threads") or []
    parts = [
        req.get("system_prompt", ""),
        "",
        "## LONG_TERM_PROFILE",
        req.get("l3_profile", ""),
        "",
        "## DAY_NARRATIVE",
        req.get("l2_day", ""),
        "",
        "## HOUR_SUMMARY",
        req.get("l1_hour", ""),
        "",
        "## PREVIOUS_WINDOW_DECISION",
        json.dumps(req.get("previous_decision") or {}, ensure_ascii=False),
        "",
        "## OPEN_THREADS",
        json.dumps(open_threads, ensure_ascii=False),
        "",
        "## CURRENT_WINDOW",
        json.dumps(window, ensure_ascii=False),
        "",
        "## INSTRUCTION",
        req.get("instruction", ""),
        "",
        f"Reply language: {req.get('reply_language', 'en')}",
        "Return ONLY the strict JSON object specified in OUTPUT.",
        # Defense in depth (PCA-N-1, layer B): even though the token gate
        # already restricts callers to the PCA app, instruct the model to
        # behave as a pure text responder so a crafted prompt can't coax an
        # agentic CLI into touching the filesystem or running commands.
        "Do NOT use any tools, do NOT read or write files, and do NOT run "
        "shell commands. Produce only the JSON object as your entire output.",
    ]
    return "\n".join(parts)


def _extract_json(stdout: str) -> dict:
    """Pull the first balanced {...} JSON object out of arbitrary CLI stdout.

    Subscription CLIs may wrap the model's reply with their own framing
    (banner, model name, timing). We instruct the model to "Return ONLY the
    strict JSON object" but tolerate noise around it by extracting the first
    balanced JSON block.
    """
    text = stdout.strip()
    if not text:
        raise BridgeError(502, "empty stdout")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    start = text.find("{")
    if start < 0:
        raise BridgeError(502, f"no JSON in stdout: {text[:300]}")
    depth = 0
    end = -1
    in_str = False
    esc = False
    for i in range(start, len(text)):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
    if end < 0:
        raise BridgeError(502, f"unbalanced JSON: {text[start:start + 300]}")
    return json.loads(text[start:end])


def _run_cli(cmd: list[str], prompt: str | None, label: str) -> dict:
    try:
        res = subprocess.run(
            cmd,
            input=prompt,
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=120,
            check=False,
        )
    except FileNotFoundError as e:
        raise BridgeError(501, f"{label} CLI not installed: {e}") from e
    except subprocess.TimeoutExpired as e:
        raise BridgeError(504, f"{label} timed out after {e.timeout}s") from e
    if res.returncode != 0:
        raise BridgeError(502, f"{label} exit={res.returncode}: {(res.stderr or '')[:500]}")
    return _extract_json(res.stdout)


def run_codex(prompt: str) -> dict:
    """codex CLI (subscription, ChatGPT Plus/Pro). Prompt on stdin via exec mode.

    `--sandbox read-only` (PCA-N-1, layer B): codex `exec` is the autonomous
    mode and will run shell commands by default. read-only denies writes and
    command execution so a crafted prompt can't escape into the Termux
    filesystem. If your installed codex version rejects this flag, drop the
    two "--sandbox", "read-only" tokens below.
    """
    return _run_cli(["codex", "exec", "--sandbox", "read-only", "-"], prompt, "codex")


def run_claude(prompt: str) -> dict:
    """claude CLI (subscription, Claude Pro/Max via @anthropic-ai/claude-code).

    `-p` (print/headless) mode without --dangerously-skip-permissions cannot
    obtain tool-use approval, so it stays a non-agentic text responder.
    """
    return _run_cli(["claude", "-p", prompt, "--output-format", "text"], None, "claude")


def run_gemini(prompt: str) -> dict:
    """gemini CLI (subscription, Google AI/AI Studio). `-p` is a one-shot,
    non-interactive text query (no agentic tool loop)."""
    return _run_cli(["gemini", "-p", prompt], None, "gemini")


def dispatch(req: dict) -> dict:
    prompt = build_prompt(req)
    window = req.get("window") or {}
    log.info(
        "decide window=%s lang=%s provider=%s",
        window.get("window_id"),
        req.get("reply_language", "en"),
        PROVIDER,
    )
    if PROVIDER == "codex":
        return run_codex(prompt)
    if PROVIDER == "claude":
        return run_claude(prompt)
    if PROVIDER == "gemini":
        return run_gemini(prompt)
    raise BridgeError(500, f"unknown provider {PROVIDER}")


class Handler(BaseHTTPRequestHandler):
    server_version = "PCABridge/1.0"

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _origin_blocked(self) -> bool:
        # PCA-S-38: the Android app sends no Origin header. A browser fetch
        # from a malicious page WOULD carry one. Reject any request with an
        # Origin so a website can't CSRF the loopback bridge into burning the
        # user's subscription quota or reading transcript snippets.
        origin = self.headers.get("Origin")
        if origin:
            self._send_json(403, {"detail": f"cross-origin requests are not allowed (origin={origin})"})
            return True
        return False

    def do_GET(self):  # noqa: N802 (stdlib naming)
        if self.path.rstrip("/") == "/health":
            self._send_json(200, {"ok": True, "provider": PROVIDER})
        else:
            self._send_json(404, {"detail": "not found"})

    def _token_ok(self) -> bool:
        # Constant-time compare so a co-installed app can't byte-by-byte time
        # the token. TOKEN is always set (generated on startup), so a missing
        # or wrong header is rejected.
        supplied = self.headers.get("X-PCA-Token", "")
        return hmac.compare_digest(supplied, TOKEN)

    def do_POST(self):  # noqa: N802
        if self._origin_blocked():
            return
        if self.path.rstrip("/") != "/decide":
            self._send_json(404, {"detail": "not found"})
            return
        if not self._token_ok():
            log.warning("rejected /decide: bad or missing X-PCA-Token from %s", self.address_string())
            self._send_json(401, {"detail": "missing or invalid X-PCA-Token"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._send_json(400, {"detail": "bad Content-Length"})
            return
        if length <= 0:
            self._send_json(400, {"detail": "empty body"})
            return
        if length > MAX_BODY_BYTES:
            self._send_json(413, {"detail": f"body too large ({length} bytes)"})
            return
        raw = self.rfile.read(length)
        try:
            req = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self._send_json(400, {"detail": f"invalid JSON body: {e}"})
            return
        if not isinstance(req, dict):
            self._send_json(400, {"detail": "body must be a JSON object"})
            return
        try:
            self._send_json(200, dispatch(req))
        except BridgeError as e:
            log.warning("decide failed: %s %s", e.status, e.detail)
            self._send_json(e.status, {"detail": e.detail})
        except Exception as e:  # noqa: BLE001 — last-resort guard, keep bridge alive
            log.exception("unexpected error")
            self._send_json(500, {"detail": f"internal error: {e}"})

    def log_message(self, fmt, *args):  # quieter access log via our logger
        log.info("%s - %s", self.address_string(), fmt % args)


def main() -> None:
    global PROVIDER, TOKEN
    parser = argparse.ArgumentParser()
    # PCA-S-23: default --host to loopback. A bridge listening on 0.0.0.0 in a
    # Wi-Fi network with no auth lets every device on the LAN POST arbitrary
    # LlmRequest, burn the user's subscription quota, and read transcripts.
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8765, type=int)
    parser.add_argument(
        "--provider", default="codex", choices=["codex", "claude", "gemini"],
        help="Which subscription CLI to invoke. All authenticate via their own "
             "login flow (no API keys needed in this process).",
    )
    parser.add_argument(
        "--token-file", default=DEFAULT_TOKEN_FILE,
        help="Path to the shared-secret token file (auto-generated on first run).",
    )
    args = parser.parse_args()
    PROVIDER = args.provider

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    TOKEN = load_or_create_token(args.token_file)
    log.info("=" * 64)
    log.info("BRIDGE TOKEN — paste into PCA app: Settings -> Bridge token")
    log.info("    %s", TOKEN)
    log.info("(stored in %s — keep it private)", args.token_file)
    log.info("=" * 64)

    if args.host == "0.0.0.0":
        log.warning(
            "BIND ALL INTERFACES: bridge is reachable from every device on this "
            "network with no authentication. Anyone on the same Wi-Fi can POST "
            "/decide to burn your %s subscription and read transcript snippets. "
            "Prefer --host 127.0.0.1 + an SSH tunnel.",
            args.provider,
        )

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    log.info("PCA bridge listening on http://%s:%d (provider=%s)", args.host, args.port, PROVIDER)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        log.info("shutting down")
        httpd.shutdown()


if __name__ == "__main__":
    main()
