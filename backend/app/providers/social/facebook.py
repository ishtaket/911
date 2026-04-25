"""Facebook public provider (placeholder — public posts/pages only, never private).

Real implementation must use Meta Graph API for public Pages/Groups only.
"""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class FacebookPublicProvider(SocialSearchProvider):
    name = "facebook_public"
    network = "facebook"

    def __init__(self, app_id: str | None, app_secret: str | None) -> None:
        self.app_id = app_id
        self.app_secret = app_secret
        self._fallback = MockSocialSearchProvider(network="facebook")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement Meta Graph API call to public Pages/Groups search.
        return await self._fallback.search(query, language=language, limit=limit)
