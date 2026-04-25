"""Facebook public provider (placeholder — public Pages only, never private).

Strict mode: missing OAuth credentials raises ProviderAuthRequired so the
operator UI shows "auth_required" instead of fabricating fake results
via a silent mock fallback. Real implementation must use the Meta Graph
API for public Pages/Groups only.
"""
from __future__ import annotations

from app.providers.base import ProviderAuthRequired, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class FacebookPublicProvider(SocialSearchProvider):
    name = "facebook_public"
    network = "facebook"

    def __init__(self, app_id: str | None, app_secret: str | None) -> None:
        self.app_id = app_id
        self.app_secret = app_secret

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not (self.app_id and self.app_secret):
            raise ProviderAuthRequired(
                "Meta Graph API credentials not configured (META_APP_ID / "
                "META_APP_SECRET) and OAuth flow not completed."
            )
        # TODO: implement Meta Graph API call to public Pages/Groups search.
        raise ProviderAuthRequired(
            "Facebook public search real-API call is not implemented yet; "
            "treating as auth_required rather than silently mocking."
        )
