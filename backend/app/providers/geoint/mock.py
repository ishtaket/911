"""Mock GeoINT provider — returns deterministic Israel-centered candidates."""
from __future__ import annotations

from app.providers.base import GeoIntProvider
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntCandidate, GeoIntResult, GeoTag


class MockGeoIntProvider(GeoIntProvider):
    name = "mock_geoint"

    async def analyze(
        self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
    ) -> GeoIntResult:
        # Default: Tel Aviv-ish, Jerusalem-ish, Haifa-ish.
        candidates = [
            GeoIntCandidate(
                lat=32.0853,
                lon=34.7818,
                place_name="Tel Aviv-Yafo (mock)",
                confidence=0.45,
                evidence=["mock OCR detected likely sign", "mock vision detected coast line"],
                contradictions=[],
                risk="low",
                next_checks=["maps poi", "street view confirm"],
                sources=["mock_geoint"],
            ),
            GeoIntCandidate(
                lat=31.7683,
                lon=35.2137,
                place_name="Jerusalem (mock)",
                confidence=0.30,
                evidence=["mock vision detected old-city wall texture"],
                contradictions=["palette differs from primary candidate"],
                risk="medium",
                next_checks=["landmark match"],
                sources=["mock_geoint"],
            ),
            GeoIntCandidate(
                lat=32.7940,
                lon=34.9896,
                place_name="Haifa (mock)",
                confidence=0.20,
                evidence=[],
                contradictions=["weak signal"],
                risk="medium",
                next_checks=["additional photos"],
                sources=["mock_geoint"],
            ),
        ]
        return GeoIntResult(
            case_id=request.case_id,
            media_id=request.media_id,
            candidates=candidates,
            exif_geotag=GeoTag(lat=32.0853, lon=34.7818, source="exif", accuracy_m=50.0)
            if image_bytes
            else None,
            extracted_text=["[mock OCR]"],
            detected_landmarks=["[mock landmark]"],
        )
