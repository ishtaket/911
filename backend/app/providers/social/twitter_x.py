"""X (Twitter) public-search provider (placeholder).

Strict mode: missing bearer token raises ProviderAuthRequired so the
operator UI shows "auth_required" instead of fabricating fake results
via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class XPublicProvider(SocialSearchProvider):
    name = "x_public"
    network = "x"

    def __init__(self, bearer_token: str | None = None) -> None:
        self.bearer_token = bearer_token

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.bearer_token:
            raise ProviderAuthRequired(
                "X (Twitter) API bearer token not configured."
            )
        # TODO: implement X API v2 recent search (public tweets only).
        raise ProviderAuthRequired(
            "X public search real-API call is not implemented yet; treating "
            "as auth_required rather than silently mocking."
        )
