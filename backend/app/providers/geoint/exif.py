"""EXIF parser provider — extracts GPS / timestamp / device from image bytes."""
from __future__ import annotations

from app.schemas.geoint import GeoTag


class ExifProvider:
    name = "exif"

    async def extract(self, image_bytes: bytes) -> dict:
        """Return a dict with optional 'geotag', 'timestamp', 'device'.

        Real implementation should use a library like exif/piexif/Pillow.
        This mock returns empty data when no real parser is configured.
        """
        # TODO: implement real EXIF extraction (Pillow.ExifTags / piexif).
        return {"geotag": None, "timestamp": None, "device": None}

    async def extract_geotag(self, image_bytes: bytes) -> GeoTag | None:
        data = await self.extract(image_bytes)
        return data.get("geotag")
