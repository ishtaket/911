"""YouTube Data API public provider (placeholder)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class YouTubePublicProvider(SocialSearchProvider):
    name = "youtube_public"
    network = "youtube"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockSocialSearchProvider(network="youtube")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement YouTube Data API v3 search.list.
        return await self._fallback.search(query, language=language, limit=limit)
