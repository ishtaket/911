"""Build archive lookup targets *only* from URLs already present in the
case's evidence. Strict priority:

  P1 — exact URLs taken from `evidence.url`
  P2 — URLs extracted from `evidence.snippet` / `evidence.raw`
  P3 — domain wildcards (`host/*`) derived ONLY from P1/P2 URLs — never
       from a static domain list.

If no URLs exist for the case → return [] and the orchestrator emits an
`archive_no_targets` audit so the operator/UI can be told to run web or
social search first.

Anti-rule: we do NOT broaden P3 to apex registrable domains
(e.g. don't infer `co.il/*`). We only emit `host/*` for each anchor URL,
plus `host/*` with a leading `www.` stripped when present. That keeps
wildcards strictly tied to evidence the operator is already looking at.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urlsplit
from uuid import UUID

from app.schemas.evidence import Evidence

# Match http(s):// URLs in free text, stopping at common delimiters.
_URL_RE = re.compile(r"https?://[^\s'\"<>)\\]+", re.IGNORECASE)


@dataclass(frozen=True)
class ArchiveTarget:
    priority: int                      # 1, 2, or 3
    url_or_pattern: str                # full URL or "host/*"
    is_pattern: bool                   # True for P3 wildcards
    anchor_evidence_id: str | None     # which evidence this target came from


def build_targets(evidence: Iterable[Evidence], limit: int = 60) -> list[ArchiveTarget]:
    """Pure function: produce P1/P2/P3 targets from a case's evidence.

    Output is ordered (P1, then P2, then P3) and de-duplicated by
    `url_or_pattern`. The first occurrence wins so the highest priority
    is preserved.
    """
    seen: set[str] = set()
    out: list[ArchiveTarget] = []

    p1: list[ArchiveTarget] = []
    p2: list[ArchiveTarget] = []
    anchor_urls: list[tuple[str, str | None]] = []  # (url, evidence_id)

    for e in evidence:
        eid = str(e.id) if e.id is not None else None

        # ---- P1 — explicit evidence.url
        if e.url and _looks_like_url(e.url):
            t = ArchiveTarget(priority=1, url_or_pattern=e.url, is_pattern=False, anchor_evidence_id=eid)
            if t.url_or_pattern not in seen:
                p1.append(t)
                seen.add(t.url_or_pattern)
                anchor_urls.append((e.url, eid))

        # ---- P2 — URLs extracted from snippet / raw
        for raw_url in _extract_urls_from_text((e.snippet or "") + " " + _raw_as_text(e)):
            if raw_url == e.url:
                continue  # already a P1 anchor
            if raw_url in seen:
                continue
            p2.append(ArchiveTarget(priority=2, url_or_pattern=raw_url, is_pattern=False, anchor_evidence_id=eid))
            seen.add(raw_url)
            anchor_urls.append((raw_url, eid))

    # ---- P3 — wildcards from P1+P2 hosts only
    p3: list[ArchiveTarget] = []
    for url, eid in anchor_urls:
        host = _host_of(url)
        if not host:
            continue
        # host/*
        pat = f"{host}/*"
        if pat not in seen:
            p3.append(ArchiveTarget(priority=3, url_or_pattern=pat, is_pattern=True, anchor_evidence_id=eid))
            seen.add(pat)
        # also a www-stripped variant when applicable — same evidence anchor,
        # so we are not broadening to unrelated domains.
        if host.startswith("www.") and host.count(".") >= 2:
            stripped = host[4:]
            pat2 = f"{stripped}/*"
            if pat2 not in seen:
                p3.append(ArchiveTarget(priority=3, url_or_pattern=pat2, is_pattern=True, anchor_evidence_id=eid))
                seen.add(pat2)

    out = p1 + p2 + p3
    return out[:limit]


# ---------- helpers ----------

def _looks_like_url(s: str) -> bool:
    return s.startswith(("http://", "https://"))


def _host_of(url: str) -> str | None:
    try:
        parts = urlsplit(url)
        return parts.hostname  # already lowercased by urlsplit
    except (ValueError, AttributeError):
        return None


def _extract_urls_from_text(text: str) -> list[str]:
    if not text:
        return []
    urls: list[str] = []
    for m in _URL_RE.finditer(text):
        u = m.group(0).rstrip(".,;:)]>")
        if _looks_like_url(u):
            urls.append(u)
    return urls


def _raw_as_text(e: Evidence) -> str:
    """Serialize the evidence's auxiliary fields (no `raw` on Evidence today
    but reserved for future use) so URL extraction can reach them."""
    raw = getattr(e, "raw", None)
    if not raw:
        return ""
    try:
        return json.dumps(raw, default=str, ensure_ascii=False)
    except Exception:  # noqa: BLE001
        return ""
