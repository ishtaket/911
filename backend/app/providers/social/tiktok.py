"""TikTok public provider (placeholder)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class TikTokPublicProvider(SocialSearchProvider):
    name = "tiktok_public"
    network = "tiktok"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key
        self._fallback = MockSocialSearchProvider(network="tiktok")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement TikTok Research/Display API when available.
        return await self._fallback.search(query, language=language, limit=limit)
