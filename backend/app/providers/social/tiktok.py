"""TikTok public provider (placeholder).

Strict mode: missing OAuth credentials raises ProviderAuthRequired so
the operator UI shows "auth_required" instead of fabricating fake
results via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class TikTokPublicProvider(SocialSearchProvider):
    name = "tiktok_public"
    network = "tiktok"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        raise ProviderAuthRequired(
            "TikTok Research/Display API requires OAuth; not configured. "
            "Treating as auth_required rather than silently mocking."
        )
