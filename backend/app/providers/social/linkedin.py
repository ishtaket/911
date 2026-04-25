"""LinkedIn public-result provider (placeholder, surfaces only via web search)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class LinkedInPublicProvider(SocialSearchProvider):
    name = "linkedin_public"
    network = "linkedin"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key
        self._fallback = MockSocialSearchProvider(network="linkedin")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: LinkedIn does not offer broad public search; rely on web-search snippets only.
        return await self._fallback.search(query, language=language, limit=limit)
