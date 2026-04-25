"""Mock social-search provider — returns deterministic fake public posts."""
from __future__ import annotations

import hashlib

from app.providers.base import SocialSearchProvider
from app.schemas.provider_result import ProviderResult, SourceType


class MockSocialSearchProvider(SocialSearchProvider):
    name = "mock_social_search"
    network = "mock"

    def __init__(self, network: str = "mock") -> None:
        self.network = network
        self.name = f"mock_{network}_search"

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        results: list[ProviderResult] = []
        for i in range(min(2, limit)):
            content_hash = hashlib.sha256(f"{self.network}|{query}|{i}".encode()).hexdigest()
            results.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.SOCIAL,
                    title=f"[mock {self.network}] public post {i + 1} for: {query}",
                    url=f"https://example.org/{self.network}/mock/{i}?q={query}",
                    snippet=f"Mock public {self.network} post for query '{query}' (lang={language}).",
                    language=language,
                    confidence=0.3,
                    risk_flags=["unverified"],
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return results
