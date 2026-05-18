"""Reference HTTP bridge for the PCA Android app.

The Android device cannot run `codex` or Gemini CLIs natively (spec §6.4). This
tiny FastAPI server fronts whichever CLI you have installed on a paired
machine (Termux, a home server, your laptop on the same Wi-Fi) and exposes a
single endpoint that the app POSTs to.

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


def run_codex(prompt: str) -> dict:
    """Invoke `codex` CLI in non-interactive mode."""
    cmd = ["codex", "exec", "--json"]
    res = subprocess.run(
        cmd, input=prompt, capture_output=True, text=True, encoding="utf-8",
        timeout=120, check=False,
    )
    if res.returncode != 0:
        raise HTTPException(status_code=502, detail=f"codex exit={res.returncode}: {res.stderr[:500]}")
    try:
        return json.loads(res.stdout)
    except json.JSONDecodeError as e:
        raise HTTPException(status_code=502, detail=f"codex non-JSON: {e}\n{res.stdout[:500]}") from e


def run_gemini(prompt: str) -> dict:
    """Invoke `gemini` CLI."""
    cmd = ["gemini", "-y", "-q", prompt]
    res = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8",
        timeout=120, check=False,
    )
    if res.returncode != 0:
        raise HTTPException(status_code=502, detail=f"gemini exit={res.returncode}: {res.stderr[:500]}")
    return json.loads(res.stdout)


PROVIDER = os.environ.get("PCA_BRIDGE_PROVIDER", "codex").lower()


@app.post("/decide")
def decide(req: LlmRequest) -> dict:
    prompt = build_prompt(req)
    log.info("decide window=%s lang=%s primary=%s", req.window.window_id, req.reply_language, PROVIDER)
    try:
        if PROVIDER == "codex":
            return run_codex(prompt)
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
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", default=8765, type=int)
    parser.add_argument("--provider", default=PROVIDER, choices=["codex", "gemini"])
    args = parser.parse_args()

    os.environ["PCA_BRIDGE_PROVIDER"] = args.provider
    PROVIDER = args.provider

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    import uvicorn  # noqa: WPS433
    uvicorn.run("server:app", host=args.host, port=args.port, log_level="info")
