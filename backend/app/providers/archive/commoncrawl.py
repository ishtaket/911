"""Common Crawl CDXJ provider (placeholder)."""
from __future__ import annotations

from app.providers.archive.mock import MockArchiveProvider
from app.providers.base import ArchiveProvider
from app.schemas.provider_result import ProviderResult


class CommonCrawlArchiveProvider(ArchiveProvider):
    name = "commoncrawl_cdxj"

    def __init__(self) -> None:
        self._fallback = MockArchiveProvider()

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # TODO: query Common Crawl CDXJ index (e.g. CC-MAIN-*) and parse WARC records.
        return await self._fallback.lookup(url_or_query, limit=limit)
