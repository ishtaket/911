"""Reddit public-search provider (placeholder)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class RedditPublicProvider(SocialSearchProvider):
    name = "reddit_public"
    network = "reddit"

    def __init__(self, client_id: str | None, client_secret: str | None) -> None:
        self.client_id = client_id
        self.client_secret = client_secret
        self._fallback = MockSocialSearchProvider(network="reddit")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement official Reddit OAuth2 search endpoint.
        return await self._fallback.search(query, language=language, limit=limit)
