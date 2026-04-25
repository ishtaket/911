"""Instagram public provider (placeholder — public profiles/hashtags only)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class InstagramPublicProvider(SocialSearchProvider):
    name = "instagram_public"
    network = "instagram"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key
        self._fallback = MockSocialSearchProvider(network="instagram")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement official IG Graph API for public business/creator accounts.
        return await self._fallback.search(query, language=language, limit=limit)
