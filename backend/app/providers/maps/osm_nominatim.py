"""OpenStreetMap Nominatim provider (placeholder; requires polite usage)."""
from __future__ import annotations

from app.providers.base import MapsProvider
from app.providers.maps.mock import MockMapsProvider
from app.schemas.provider_result import ProviderResult


class OsmNominatimProvider(MapsProvider):
    name = "osm_nominatim"

    def __init__(self, user_agent: str = "Rescue911-OSINT/0.1 (contact@rescue911.local)") -> None:
        self.user_agent = user_agent
        self._fallback = MockMapsProvider()

    async def validate_place(self, lat: float, lon: float, language: str = "en") -> ProviderResult:
        # TODO: call https://nominatim.openstreetmap.org/reverse with proper UA + rate-limit.
        return await self._fallback.validate_place(lat, lon, language=language)

    async def geocode(
        self, query: str, region: str = "IL", language: str = "en"
    ) -> list[ProviderResult]:
        # TODO: call /search?countrycodes=il&accept-language=he,en,ru,ar.
        return await self._fallback.geocode(query, region=region, language=language)
