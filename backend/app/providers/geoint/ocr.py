"""OCR provider — extracts visible text in en/he/ru/ar."""
from __future__ import annotations


class OcrProvider:
    name = "ocr"

    async def extract_text(self, image_bytes: bytes, languages: list[str] | None = None) -> list[str]:
        """Return list of detected strings.

        Real implementation can wrap Tesseract, Google Vision, or Azure Vision OCR.
        """
        # TODO: implement multi-script OCR (Hebrew + Arabic + Cyrillic + Latin).
        _ = languages
        return []
