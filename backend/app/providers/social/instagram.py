"""Instagram public provider (placeholder — public profiles/hashtags only).

Strict mode: missing credentials raises ProviderAuthRequired so the
operator UI shows "auth_required" instead of fabricating fake results
via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class InstagramPublicProvider(SocialSearchProvider):
    name = "instagram_public"
    network = "instagram"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # IG public search uses Meta OAuth + Graph API for business/creator accounts.
        raise ProviderAuthRequired(
            "Instagram public search requires Meta Graph API OAuth; not "
            "configured. Treating as auth_required rather than silently mocking."
        )
