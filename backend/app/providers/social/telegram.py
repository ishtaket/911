"""Telegram public-channel provider (placeholder — public channels only).

Strict mode: missing api_id/api_hash raises ProviderNotConfigured so
the operator UI shows "not_configured" instead of fabricating fake
results via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderNotConfigured, SocialSearchProvider
from app.schemas.provider_result import ProviderResult


class TelegramPublicProvider(SocialSearchProvider):
    name = "telegram_public"
    network = "telegram"

    def __init__(self, api_id: str | None, api_hash: str | None) -> None:
        self.api_id = api_id
        self.api_hash = api_hash

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not (self.api_id and self.api_hash):
            raise ProviderNotConfigured(
                "Telegram api_id/api_hash not configured "
                "(TELEGRAM_API_ID / TELEGRAM_API_HASH)."
            )
        # TODO: implement Telethon/MTProto for public channel search.
        raise ProviderNotConfigured(
            "Telegram public search real-API call is not implemented yet; "
            "treating as not_configured rather than silently mocking."
        )
