"""GeoSeer / Geo-locating-by-image provider (placeholder)."""
from __future__ import annotations

from app.providers.base import GeoIntProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult


class GeoSeerProvider(GeoIntProvider):
    name = "geoseer"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockGeoIntProvider()

    async def analyze(
        self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
    ) -> GeoIntResult:
        # TODO: call GeoSeer image-geolocation endpoint; bias to region_hint=IL.
        return await self._fallback.analyze(request, image_bytes=image_bytes)
