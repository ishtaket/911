"""LocationIQ / OSM provider (placeholder)."""
from __future__ import annotations

from app.providers.base import MapsProvider
from app.providers.maps.mock import MockMapsProvider
from app.schemas.provider_result import ProviderResult


class LocationIqProvider(MapsProvider):
    name = "locationiq"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockMapsProvider()

    async def validate_place(self, lat: float, lon: float, language: str = "en") -> ProviderResult:
        # TODO: call LocationIQ /v1/reverse + /v1/nearby.
        return await self._fallback.validate_place(lat, lon, language=language)

    async def geocode(
        self, query: str, region: str = "IL", language: str = "en"
    ) -> list[ProviderResult]:
        # TODO: call LocationIQ /v1/search?countrycodes=il&accept-language=he,en,ru,ar.
        return await self._fallback.geocode(query, region=region, language=language)
