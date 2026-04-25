"""Reddit public-search provider (placeholder).

Strict mode: missing OAuth client raises ProviderAuthRequired so the
operator UI shows "auth_required" instead of fabricating fake results
via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class RedditPublicProvider(SocialSearchProvider):
    name = "reddit_public"
    network = "reddit"

    def __init__(self, client_id: str | None, client_secret: str | None) -> None:
        self.client_id = client_id
        self.client_secret = client_secret

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not (self.client_id and self.client_secret):
            raise ProviderAuthRequired(
                "Reddit OAuth credentials not configured "
                "(REDDIT_CLIENT_ID / REDDIT_CLIENT_SECRET)."
            )
        # TODO: implement official Reddit OAuth2 search endpoint.
        raise ProviderAuthRequired(
            "Reddit public search real-API call is not implemented yet; "
            "treating as auth_required rather than silently mocking."
        )
