"""X (Twitter) public-search provider (placeholder)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class XPublicProvider(SocialSearchProvider):
    name = "x_public"
    network = "x"

    def __init__(self, bearer_token: str | None = None) -> None:
        self.bearer_token = bearer_token
        self._fallback = MockSocialSearchProvider(network="x")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement X API v2 recent search (public tweets only).
        return await self._fallback.search(query, language=language, limit=limit)
