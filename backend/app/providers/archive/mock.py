"""Mock archive provider — returns deterministic fake snapshots."""
from __future__ import annotations

import hashlib
from datetime import datetime, timedelta

from app.providers.base import ArchiveProvider
from app.schemas.provider_result import ProviderResult, SourceType


class MockArchiveProvider(ArchiveProvider):
    name = "mock_archive"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        results: list[ProviderResult] = []
        for i in range(min(2, limit)):
            content_hash = hashlib.sha256(f"archive|{url_or_query}|{i}".encode()).hexdigest()
            captured = datetime.utcnow() - timedelta(days=180 + i * 90)
            results.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.ARCHIVE,
                    title=f"[mock archive] snapshot {i + 1} for: {url_or_query}",
                    url=f"https://example.org/archive/mock/{i}",
                    snippet="Mock archive snapshot — public indexed mirror.",
                    captured_at=captured,
                    confidence=0.25,  # archive-only data is lower confidence
                    risk_flags=["archive_only"],
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return results
