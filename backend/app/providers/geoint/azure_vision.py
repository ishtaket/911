"""Azure Vision provider (placeholder)."""
from __future__ import annotations

from app.providers.base import GeoIntProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult


class AzureVisionProvider(GeoIntProvider):
    name = "azure_vision"

    def __init__(self, endpoint: str | None, key: str | None) -> None:
        self.endpoint = endpoint
        self.key = key
        self._fallback = MockGeoIntProvider()

    async def analyze(
        self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
    ) -> GeoIntResult:
        # TODO: call Azure Image Analysis (read OCR + tags + caption); fold into GeoIntResult.
        return await self._fallback.analyze(request, image_bytes=image_bytes)
