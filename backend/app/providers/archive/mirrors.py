"""Public-mirror provider — known re-hosters of public content (placeholder)."""
from __future__ import annotations

from app.providers.archive.mock import MockArchiveProvider
from app.providers.base import ArchiveProvider
from app.schemas.provider_result import ProviderResult


class PublicMirrorArchiveProvider(ArchiveProvider):
    name = "public_mirror"

    def __init__(self) -> None:
        self._fallback = MockArchiveProvider()

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        # TODO: query a curated allow-list of public mirror sites.
        return await self._fallback.lookup(url_or_query, limit=limit)
