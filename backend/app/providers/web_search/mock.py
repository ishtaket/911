"""Mock web-search provider — used when no real key is configured."""
from __future__ import annotations

import hashlib

from app.providers.base import WebSearchProvider
from app.schemas.provider_result import ProviderResult, SourceType


class MockWebSearchProvider(WebSearchProvider):
    name = "mock_web_search"

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        results: list[ProviderResult] = []
        for i in range(min(3, limit)):
            content_hash = hashlib.sha256(f"{query}|{i}".encode()).hexdigest()
            results.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.WEB,
                    title=f"[mock] result {i + 1} for: {query}",
                    url=f"https://example.org/mock/{i}?q={query}",
                    snippet=f"Mock public web search snippet for query '{query}' (lang={language}).",
                    language=language,
                    confidence=0.4 - i * 0.1,
                    risk_flags=[],
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return results
