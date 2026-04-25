"""Mock providers return well-formed `ProviderResult`s."""
from __future__ import annotations

import pytest

from app.providers.archive.mock import MockArchiveProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.providers.maps.mock import MockMapsProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.providers.web_search.mock import MockWebSearchProvider
from app.schemas.geoint import GeoIntAnalyzeRequest
from app.schemas.provider_result import SourceType


@pytest.mark.asyncio
async def test_mock_web_search() -> None:
    p = MockWebSearchProvider()
    out = await p.search("missing", language="en")
    assert out
    assert out[0].source_type == SourceType.WEB


@pytest.mark.asyncio
async def test_mock_social_search() -> None:
    p = MockSocialSearchProvider(network="telegram")
    out = await p.search("missing", language="he")
    assert out
    assert out[0].source_type == SourceType.SOCIAL
    assert "telegram" in out[0].provider


@pytest.mark.asyncio
async def test_mock_archive() -> None:
    p = MockArchiveProvider()
    out = await p.lookup("https://example.org")
    assert out
    assert out[0].source_type == SourceType.ARCHIVE
    assert "archive_only" in out[0].risk_flags


@pytest.mark.asyncio
async def test_mock_geoint_returns_il_candidates() -> None:
    p = MockGeoIntProvider()
    from uuid import uuid4

    req = GeoIntAnalyzeRequest(case_id=uuid4(), media_id=uuid4())
    res = await p.analyze(req)
    assert res.candidates
    # At least one candidate should be inside Israel bbox
    assert any(31.2 <= c.lat <= 33.5 and 34.2 <= c.lon <= 35.9 for c in res.candidates)


@pytest.mark.asyncio
async def test_mock_maps() -> None:
    p = MockMapsProvider()
    out = await p.geocode("Tel Aviv", region="IL", language="he")
    assert out
    assert out[0].source_type == SourceType.MAPS
