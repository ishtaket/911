"""Telegram public-channel provider (placeholder — public channels only)."""
from __future__ import annotations

from app.providers.base import SocialSearchProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.schemas.provider_result import ProviderResult


class TelegramPublicProvider(SocialSearchProvider):
    name = "telegram_public"
    network = "telegram"

    def __init__(self, api_id: str | None, api_hash: str | None) -> None:
        self.api_id = api_id
        self.api_hash = api_hash
        self._fallback = MockSocialSearchProvider(network="telegram")

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # TODO: implement Telethon/MTProto for public channel search.
        return await self._fallback.search(query, language=language, limit=limit)
