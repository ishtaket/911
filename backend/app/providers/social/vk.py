"""VK public-search provider (placeholder).

Strict mode: missing access token raises ProviderAuthRequired so the
operator UI shows "auth_required" instead of fabricating fake results
via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class VkPublicProvider(SocialSearchProvider):
    name = "vk_public"
    network = "vk"

    def __init__(self, access_token: str | None = None) -> None:
        self.access_token = access_token

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.access_token:
            raise ProviderAuthRequired(
                "VK API access token not configured."
            )
        # TODO: implement VK API search method (public profiles/groups).
        raise ProviderAuthRequired(
            "VK public search real-API call is not implemented yet; treating "
            "as auth_required rather than silently mocking."
        )
