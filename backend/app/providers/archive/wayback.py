"""Wayback Machine CDX provider — public archive snapshots."""
from __future__ import annotations

import hashlib
from datetime import datetime

import httpx

from app.providers.archive.mock import MockArchiveProvider
from app.providers.base import ArchiveProvider
from app.schemas.provider_result import ProviderResult, SourceType

CDX_ENDPOINT = "https://web.archive.org/cdx/search/cdx"


class WaybackArchiveProvider(ArchiveProvider):
    name = "wayback_cdx"

    def __init__(self) -> None:
        self._fallback = MockArchiveProvider()

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        params = {"url": url_or_query, "output": "json", "limit": str(limit)}
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.get(CDX_ENDPOINT, params=params)
                resp.raise_for_status()
                data = resp.json()
        except Exception:
            return await self._fallback.lookup(url_or_query, limit=limit)

        if not data or len(data) <= 1:
            return []

        out: list[ProviderResult] = []
        for row in data[1 : limit + 1]:
            try:
                timestamp, original = row[1], row[2]
                snapshot_url = f"https://web.archive.org/web/{timestamp}/{original}"
                captured_at = datetime.strptime(timestamp, "%Y%m%d%H%M%S")
            except Exception:
                continue
            content_hash = hashlib.sha256(f"wayback|{snapshot_url}".encode()).hexdigest()
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.ARCHIVE,
                    title=original,
                    url=snapshot_url,
                    captured_at=captured_at,
                    confidence=0.35,
                    risk_flags=["archive_only"],
                    is_legal_source=True,
                    content_hash=content_hash,
                    raw={"row": row},
                )
            )
        return out
