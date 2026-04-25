"""Mock maps provider — returns deterministic Israel POIs."""
from __future__ import annotations

from app.providers.base import MapsProvider
from app.schemas.provider_result import ProviderResult, SourceType


class MockMapsProvider(MapsProvider):
    name = "mock_maps"

    async def validate_place(self, lat: float, lon: float, language: str = "en") -> ProviderResult:
        return ProviderResult(
            provider=self.name,
            source_type=SourceType.MAPS,
            title=f"[mock POI] near ({lat:.4f}, {lon:.4f})",
            url=None,
            snippet=f"Mock POI validation for ({lat:.4f}, {lon:.4f}) in {language}.",
            language=language,
            confidence=0.5,
            is_legal_source=True,
            raw={"lat": lat, "lon": lon},
        )

    async def geocode(
        self, query: str, region: str = "IL", language: str = "en"
    ) -> list[ProviderResult]:
        return [
            ProviderResult(
                provider=self.name,
                source_type=SourceType.MAPS,
                title=f"[mock geocode] {query}, IL",
                url=None,
                snippet=f"Mock geocode for '{query}' in region {region}.",
                language=language,
                confidence=0.4,
                is_legal_source=True,
                raw={"query": query, "lat": 32.0853, "lon": 34.7818},
            )
        ]
