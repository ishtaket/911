"""VK public-search provider (placeholder)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class VkPublicProvider(SocialSearchProvider):
    name = "vk_public"
    network = "vk"

    def __init__(self, access_token: str | None = None) -> None:
        self.access_token = access_token
        self._fallback = MockSocialSearchProvider(network="vk")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement VK API search method (public profiles/groups).
        return await self._fallback.search(query, language=language, limit=limit)
