"""LinkedIn public-result provider (placeholder).

Strict mode: missing OAuth credentials raises ProviderAuthRequired so
the operator UI shows "auth_required" instead of fabricating fake
results via a silent mock fallback. LinkedIn's public surface is
limited; broader coverage flows through web-search snippets.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class LinkedInPublicProvider(SocialSearchProvider):
    name = "linkedin_public"
    network = "linkedin"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        raise ProviderAuthRequired(
            "LinkedIn Marketing API requires OAuth; not configured. Treating "
            "as auth_required rather than silently mocking."
        )
