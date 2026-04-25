"""Wayback Machine CDX provider — public archive snapshot index.

Public Internet Archive endpoint. **No API key required.**
Docs: https://archive.org/developers/wayback-cdx-server.html

This provider is strict: it does not silently fall back to mock data.
On transport / rate-limit failure the orchestrator's audit log records
the error per provider and moves on.
"""
from __future__ import annotations

import hashlib
from datetime import datetime

import httpx

from app.providers.base import ArchiveProvider, RateLimitError
from app.schemas.provider_result import ProviderResult, SourceType

CDX_ENDPOINT = "https://web.archive.org/cdx/search/cdx"
TIMEOUT_SEC = 15.0


class WaybackArchiveProvider(ArchiveProvider):
    name = "wayback_cdx"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # CDX requires a URL or URL-prefix. If the caller passed a free-text
        # query, there's nothing to look up.
        if not url_or_query.startswith(("http://", "https://")) and "." not in url_or_query:
            return []

        params = {
            "url": url_or_query,
            "output": "json",
            "fl": "timestamp,original,statuscode,mimetype,digest",
            "filter": "statuscode:200",
            "collapse": "digest",
            "limit": str(limit),
        }
        async with httpx.AsyncClient(timeout=TIMEOUT_SEC) as client:
            resp = await client.get(CDX_ENDPOINT, params=params)
            if resp.status_code == 429:
                raise RateLimitError("Wayback CDX rate-limited (429)")
            resp.raise_for_status()
            data = resp.json()

        # CDX JSON: first row is the field names header, subsequent rows are
        # values. With our `fl=` order: [timestamp, original, statuscode, mimetype, digest].
        if not data or len(data) <= 1:
            return []

        out: list[ProviderResult] = []
        for row in data[1 : limit + 1]:
            try:
                timestamp, original, statuscode, mimetype, digest = row[:5]
            except (ValueError, TypeError):
                continue
            snapshot_url = f"https://web.archive.org/web/{timestamp}/{original}"
            try:
                captured_at = datetime.strptime(timestamp, "%Y%m%d%H%M%S")
            except (ValueError, TypeError):
                captured_at = None
            content_hash = hashlib.sha256(f"wayback|{snapshot_url}|{digest}".encode()).hexdigest()
            snippet = f"timestamp={timestamp} status={statuscode} mime={mimetype} original={original}"
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.ARCHIVE,
                    title="Wayback snapshot",
                    url=snapshot_url,
                    snippet=snippet,
                    captured_at=captured_at,
                    confidence=0.60,
                    is_legal_source=True,
                    content_hash=content_hash,
                    raw={
                        "query_used": url_or_query,
                        "legal_basis": "public_archive",
                        "timestamp": timestamp,
                        "original": original,
                        "statuscode": statuscode,
                        "mimetype": mimetype,
                        "digest": digest,
                    },
                )
            )
        return out
