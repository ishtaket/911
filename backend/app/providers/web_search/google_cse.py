"""Google Custom Search Engine provider (placeholder)."""
from __future__ import annotations

from app.providers.base import WebSearchProvider
from app.providers.web_search.mock import MockWebSearchProvider
from app.schemas.provider_result import ProviderResult


class GoogleCseWebSearchProvider(WebSearchProvider):
    name = "google_cse"

    def __init__(self, api_key: str | None, cse_id: str | None = None) -> None:
        self.api_key = api_key
        self.cse_id = cse_id
        self._fallback = MockWebSearchProvider()

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement real Google CSE call when keys are provided.
        return await self._fallback.search(query, language=language, limit=limit)
