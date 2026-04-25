"""OpenAI Vision reasoner (placeholder).

Used as a *reasoner* over candidates produced by other providers (EXIF, OCR,
GeoSeer, Picarta) — not as a single source of truth. Output must be merged
into `GeoIntResult.candidates` with confidence + contradictions.
"""
from __future__ import annotations

from app.providers.base import GeoIntProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult


class OpenAiVisionReasoner(GeoIntProvider):
    name = "openai_vision_reasoner"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockGeoIntProvider()

    async def analyze(
        self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
    ) -> GeoIntResult:
        # TODO: call OpenAI vision-capable model; pass other-provider candidates as context.
        return await self._fallback.analyze(request, image_bytes=image_bytes)
