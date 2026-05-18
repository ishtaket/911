"""Reference HTTP bridge for the PCA Android app.

The Android device cannot run `codex` or Gemini CLIs natively (spec §6.4). This
tiny FastAPI server fronts whichever CLI you have installed on a paired
machine (Termux, a home server, your laptop on the same Wi-Fi) and exposes a
single endpoint that the app POSTs to.

BILLING MODEL (spec §6.4): CLIs run under the user's SUBSCRIPTION, not via
pay-per-token API keys.
  - codex CLI  → ChatGPT Plus/Pro,   logged in via `codex login`
  - claude CLI → Claude Pro/Max,     logged in via `/login` in REPL
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

Returns the JSON object described in spec §5 — the exact shape of `LlmDecision`.

Run:
  pip install fastapi uvicorn pydantic
  python server.py --provider codex
  # or
  python server.py --provider gemini

Then point the Android app at http://<host>:8765/ in Settings → LLM provider →
HTTP bridge.

NOTE: This server runs no auth and listens on 0.0.0.0 by default. Bind to LAN
only, run behind a reverse proxy, or add token auth before exposing it. The
client wire format never contains raw PII (the anonymiser runs on-device per
spec §3.5).
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import subprocess
from typing import Any, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

log = logging.getLogger("pca-bridge")

app = FastAPI(title="PCA bridge")


class WindowPayload(BaseModel):
    window_id: int
    start_ts: int
    end_ts: int
    transcript: str
    location_label: Optional[str] = None
    is_owner_present: bool = True
    tags: list[str] = Field(default_factory=list)


class OpenThreadDto(BaseModel):
    id: str
    topic: str
    context: str = ""
    opened_at: int
    due: Optional[int] = None


class LlmRequest(BaseModel):
    system_prompt: str
    l3_profile: str
    l2_day: str
    l1_hour: str
    previous_decision: Optional[dict[str, Any]] = None
    open_threads: list[OpenThreadDto] = Field(default_factory=list)
    window: WindowPayload
    instruction: str
    reply_language: str = "en"


def build_prompt(req: LlmRequest) -> str:
    """Compose a single text prompt for codex / gemini CLI to consume on stdin."""
    parts = [
        req.system_prompt,
        "",
        "## LONG_TERM_PROFILE",
        req.l3_profile,
        "",
        "## DAY_NARRATIVE",
        req.l2_day,
        "",
        "## HOUR_SUMMARY",
        req.l1_hour,
        "",
        "## PREVIOUS_WINDOW_DECISION",
        json.dumps(req.previous_decision or {}, ensure_ascii=False),
        "",
        "## OPEN_THREADS",
        json.dumps([t.model_dump() for t in req.open_threads], ensure_ascii=False),
        "",
        "## CURRENT_WINDOW",
        json.dumps(req.window.model_dump(), ensure_ascii=False),
        "",
        "## INSTRUCTION",
        req.instruction,
        "",
        f"Reply language: {req.reply_language}",
        "Return ONLY the strict JSON object specified in OUTPUT.",
    ]
    return "\n".join(parts)


def _extract_json(stdout: str) -> dict:
    """Pull the first {...} JSON object out of arbitrary CLI stdout.

    Subscription CLIs may wrap the model's reply with their own pretty-printed
    framing (banner, model name, timing). We instruct the model in the system
    prompt to "Return ONLY the strict JSON object" but tolerate noise around
    it by extracting the first balanced JSON block.
    """
    text = stdout.strip()
    if not text:
        raise HTTPException(status_code=502, detail="empty stdout")
    # Fast path: whole stdout is JSON.
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # Slow path: scan for the first balanced {...}.
    start = text.find("{")
    if start < 0:
        raise HTTPException(status_code=502, detail=f"no JSON in stdout: {text[:300]}")
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
        raise HTTPException(status_code=502, detail=f"unbalanced JSON: {text[start:start+300]}")
    return json.loads(text[start:end])


def run_codex(prompt: str) -> dict:
    """Invoke `codex` CLI (subscription, ChatGPT Plus/Pro).

    The exact flag set varies by @openai/codex version. We feed the prompt
    on stdin via `exec` mode and parse whatever JSON the model emits.
    Customise the command list if your installed CLI uses different flags.
    """
    cmd = ["codex", "exec", "-"]
    res = subprocess.run(
        cmd, input=prompt, capture_output=True, text=True, encoding="utf-8",
        timeout=120, check=False,
    )
    if res.returncode != 0:
        raise HTTPException(status_code=502, detail=f"codex exit={res.returncode}: {res.stderr[:500]}")
    return _extract_json(res.stdout)


def run_claude(prompt: str) -> dict:
    """Invoke `claude` CLI (subscription, Claude Pro/Max via @anthropic-ai/claude-code)."""
    cmd = ["claude", "-p", prompt, "--output-format", "text"]
    res = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8",
        timeout=120, check=False,
    )
    if res.returncode != 0:
        raise HTTPException(status_code=502, detail=f"claude exit={res.returncode}: {res.stderr[:500]}")
    return _extract_json(res.stdout)


def run_gemini(prompt: str) -> dict:
    """Invoke `gemini` CLI (subscription, Google AI/AI Studio)."""
    cmd = ["gemini", "-p", prompt]
    res = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8",
        timeout=120, check=False,
    )
    if res.returncode != 0:
        raise HTTPException(status_code=502, detail=f"gemini exit={res.returncode}: {res.stderr[:500]}")
    return _extract_json(res.stdout)


PROVIDER = os.environ.get("PCA_BRIDGE_PROVIDER", "codex").lower()


@app.post("/decide")
def decide(req: LlmRequest) -> dict:
    prompt = build_prompt(req)
    log.info("decide window=%s lang=%s primary=%s", req.window.window_id, req.reply_language, PROVIDER)
    try:
        if PROVIDER == "codex":
            return run_codex(prompt)
        if PROVIDER == "claude":
            return run_claude(prompt)
        if PROVIDER == "gemini":
            return run_gemini(prompt)
        raise HTTPException(status_code=500, detail=f"unknown provider {PROVIDER}")
    except FileNotFoundError as e:
        raise HTTPException(status_code=501, detail=f"CLI not installed: {e}") from e


@app.get("/health")
def health() -> dict:
    return {"ok": True, "provider": PROVIDER}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    # PCA-S-23: default --host to loopback. A bridge listening on 0.0.0.0
    # in a Wi-Fi network with no auth lets every device on the LAN POST
    # arbitrary LlmRequest, burn the user's subscription quota, and read
    # transcripts off /decide. The Termux installer already passes
    # `--host 127.0.0.1`; this changes the default so the laptop variant
    # also stays safe-by-default. Users who run the bridge on a paired
    # device must opt in explicitly via --host 0.0.0.0 and accept the risk.
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8765, type=int)
    parser.add_argument(
        "--provider", default=PROVIDER, choices=["codex", "claude", "gemini"],
        help="Which subscription CLI to invoke. All authenticate via their own "
             "login flow (no API keys needed in this process).",
    )
    args = parser.parse_args()

    os.environ["PCA_BRIDGE_PROVIDER"] = args.provider
    PROVIDER = args.provider

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    if args.host == "0.0.0.0":
        log.warning(
            "BIND ALL INTERFACES: bridge is reachable from every device on "
            "this network with no authentication. Anyone in the same Wi-Fi "
            "can POST /decide to burn your %s subscription and read transcript "
            "snippets. Bind to a specific LAN IP and put the bridge behind a "
            "reverse proxy with auth, or use --host 127.0.0.1 + an SSH tunnel.",
            args.provider,
        )

    import uvicorn  # noqa: WPS433
    uvicorn.run("server:app", host=args.host, port=args.port, log_level="info")
