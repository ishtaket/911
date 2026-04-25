"""Wayback Availability provider — closest snapshot for a single URL.

Public Internet Archive endpoint. **No API key required.**
Docs: https://archive.org/help/wayback_api.php
"""
from __future__ import annotations

import hashlib
from datetime import datetime

import httpx

from app.providers.base import ArchiveProvider, RateLimitError
from app.schemas.provider_result import ProviderResult, SourceType

AVAIL_ENDPOINT = "https://archive.org/wayback/available"
TIMEOUT_SEC = 10.0


class WaybackAvailabilityProvider(ArchiveProvider):
    """Returns 0 or 1 result per query — the closest available snapshot."""

    name = "wayback_availability"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # The Availability API takes a *URL*, not a free-text query. If the
        # caller passed a free-text query (e.g. a person's name), there's
        # nothing useful we can do — return empty.
        if not url_or_query.startswith(("http://", "https://", "//")) and "." not in url_or_query:
            return []

        params = {"url": url_or_query}
        async with httpx.AsyncClient(timeout=TIMEOUT_SEC) as client:
            resp = await client.get(AVAIL_ENDPOINT, params=params)
            if resp.status_code == 429:
                raise RateLimitError("Wayback Availability rate-limited (429)")
            resp.raise_for_status()
            data = resp.json()

        closest = (data.get("archived_snapshots") or {}).get("closest") or {}
        if not closest or not closest.get("available"):
            return []

        snapshot_url = closest.get("url")
        timestamp = closest.get("timestamp")
        status = closest.get("status")
        captured_at: datetime | None = None
        if timestamp:
            try:
                captured_at = datetime.strptime(timestamp, "%Y%m%d%H%M%S")
            except ValueError:
                captured_at = None

        content_hash = (
            hashlib.sha256(f"wayback_avail|{snapshot_url}".encode()).hexdigest()
            if snapshot_url else None
        )
        snippet = f"timestamp={timestamp} status={status} for url={url_or_query}"

        return [
            ProviderResult(
                provider=self.name,
                source_type=SourceType.ARCHIVE,
                title="Wayback closest snapshot",
                url=snapshot_url,
                snippet=snippet,
                captured_at=captured_at,
                confidence=0.62,
                is_legal_source=True,
                content_hash=content_hash,
                raw={
                    "query_used": url_or_query,
                    "legal_basis": "public_archive",
                    "wayback_response": data,
                },
            )
        ]
