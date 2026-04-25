"""Google Vision provider (placeholder)."""
from __future__ import annotations

from app.providers.base import GeoIntProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult


class GoogleVisionProvider(GeoIntProvider):
    name = "google_vision"

    def __init__(self, credentials_path: str | None) -> None:
        self.credentials_path = credentials_path
        self._fallback = MockGeoIntProvider()

    async def analyze(
        self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
    ) -> GeoIntResult:
        # TODO: call Google Vision (label/landmark/text_detection); fold into GeoIntResult.
        return await self._fallback.analyze(request, image_bytes=image_bytes)
