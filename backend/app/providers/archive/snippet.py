"""Search-engine snippet provider — surfaces indexed snippets that may persist after page deletion."""
from __future__ import annotations

from app.providers.archive.mock import MockArchiveProvider
from app.providers.base import ArchiveProvider
from app.schemas.provider_result import ProviderResult


class SearchSnippetArchiveProvider(ArchiveProvider):
    name = "search_snippet"

    def __init__(self) -> None:
        self._fallback = MockArchiveProvider()

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # TODO: extract snippets from Brave/Google CSE results that point to dead URLs.
        return await self._fallback.lookup(url_or_query, limit=limit)
