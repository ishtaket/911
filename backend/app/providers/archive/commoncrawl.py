"""Common Crawl CDXJ provider — public web crawl index.

**No API key required.**
Docs: https://commoncrawl.org/get-started

Strategy:
  1. fetch /collinfo.json (list of indexes, newest first)
  2. query the latest 2 indexes via {index}-index?url=...&output=json
  3. each response line is a JSON object describing one capture
"""
from __future__ import annotations

import hashlib
import json
from datetime import datetime

import httpx

from app.providers.base import ArchiveProvider, RateLimitError
from app.schemas.provider_result import ProviderResult, SourceType

COLLINFO_ENDPOINT = "https://index.commoncrawl.org/collinfo.json"
TIMEOUT_SEC = 20.0
INDEXES_TO_QUERY = 2  # most recent N indexes


class CommonCrawlArchiveProvider(ArchiveProvider):
    name = "common_crawl"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # CC index keys on URL/host pattern. Free-text isn't useful here.
        if not url_or_query.startswith(("http://", "https://")) and "." not in url_or_query:
            return []

        async with httpx.AsyncClient(timeout=TIMEOUT_SEC) as client:
            collinfo = await client.get(COLLINFO_ENDPOINT)
            if collinfo.status_code == 429:
                raise RateLimitError("Common Crawl collinfo rate-limited (429)")
            collinfo.raise_for_status()
            indexes = collinfo.json()

            chosen = [c.get("id") for c in indexes[:INDEXES_TO_QUERY] if c.get("id")]
            out: list[ProviderResult] = []
            for index_id in chosen:
                resp = await client.get(
                    f"https://index.commoncrawl.org/{index_id}-index",
                    params={"url": url_or_query, "output": "json", "limit": str(limit)},
                )
                if resp.status_code == 404:
                    continue  # index has no captures for this URL
                if resp.status_code == 429:
                    raise RateLimitError(f"Common Crawl {index_id} rate-limited (429)")
                resp.raise_for_status()
                # CC returns line-delimited JSON, one capture per line
                for line in resp.text.splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        item = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    out.append(_to_result(item, index_id, url_or_query))
                    if len(out) >= limit:
                        return out
            return out


def _to_result(item: dict, index_id: str, query: str) -> ProviderResult:
    original = item.get("url")
    timestamp = item.get("timestamp")
    status = item.get("status")
    mime = item.get("mime") or item.get("mime-detected")
    digest = item.get("digest")
    captured_at: datetime | None = None
    if timestamp:
        try:
            captured_at = datetime.strptime(timestamp, "%Y%m%d%H%M%S")
        except (ValueError, TypeError):
            captured_at = None
    snippet = f"index={index_id} timestamp={timestamp} status={status} mime={mime}"
    content_hash = (
        hashlib.sha256(f"cc|{index_id}|{original}|{digest}".encode()).hexdigest()
        if original else None
    )
    return ProviderResult(
        provider="common_crawl",
        source_type=SourceType.ARCHIVE,
        title="Common Crawl capture",
        url=original,
        snippet=snippet,
        captured_at=captured_at,
        confidence=0.55,
        is_legal_source=True,
        content_hash=content_hash,
        raw={
            "query_used": query,
            "legal_basis": "public_archive",
            "index_id": index_id,
            "filename": item.get("filename"),
            "offset": item.get("offset"),
            "length": item.get("length"),
            "digest": digest,
            "timestamp": timestamp,
            "url": original,
        },
    )
