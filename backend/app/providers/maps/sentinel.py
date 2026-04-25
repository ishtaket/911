"""Sentinel Hub satellite-imagery provider (placeholder)."""
from __future__ import annotations

from app.providers.base import MapsProvider
from app.providers.maps.mock import MockMapsProvider
from app.schemas.provider_result import ProviderResult


class SentinelHubProvider(MapsProvider):
    name = "sentinel_hub"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockMapsProvider()

    async def validate_place(self, lat: float, lon: float, language: str = "en") -> ProviderResult:
        # TODO: fetch tile / mosaic for a recent date and surface a thumbnail URL.
        return await self._fallback.validate_place(lat, lon, language=language)

    async def geocode(
        self, query: str, region: str = "IL", language: str = "en"
    ) -> list[ProviderResult]:
        # Satellite providers don't geocode strings — fall through.
        return await self._fallback.geocode(query, region=region, language=language)
