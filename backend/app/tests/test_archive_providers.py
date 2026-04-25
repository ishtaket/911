"""Real archive providers — Wayback Availability, Wayback CDX, Common Crawl."""
from __future__ import annotations

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app
from app.providers.archive.commoncrawl import (
    COLLINFO_ENDPOINT,
    CommonCrawlArchiveProvider,
)
from app.providers.archive.wayback import CDX_ENDPOINT, WaybackArchiveProvider
from app.providers.archive.wayback_availability import (
    AVAIL_ENDPOINT,
    WaybackAvailabilityProvider,
)
from app.providers.registry import get_archive_providers
from app.schemas.provider_result import SourceType
from app.services.store import get_store

client = TestClient(create_app())


# ---------- registry ----------

def test_registry_archive_includes_real_providers():
    s = get_settings().model_copy(update={"mock_providers": False})
    names = [p.name for p in get_archive_providers(s)]
    assert "wayback_availability" in names
    assert "wayback_cdx" in names
    assert "common_crawl" in names
    assert "mock_archive" not in names  # strict mode


def test_registry_archive_dev_mode_keeps_mock():
    s = get_settings().model_copy(update={"mock_providers": True})
    names = [p.name for p in get_archive_providers(s)]
    assert "mock_archive" in names
    assert "wayback_availability" in names  # public providers always present


# ---------- /v1/providers ----------

def test_providers_archive_rows_are_connected_no_auth():
    body = client.get("/v1/providers").json()
    archive_ids = {"wayback_availability", "wayback_cdx", "common_crawl"}
    found = {p["provider_id"]: p for p in body["providers"] if p["provider_id"] in archive_ids}
    assert archive_ids.issubset(found.keys()), f"missing: {archive_ids - found.keys()}"
    for pid, row in found.items():
        assert row["state"] == "connected", f"{pid} state={row['state']}"
        assert row["auth_type"] == "none"
        assert row["configured"] is True
        assert row["requires_user_action"] is False


# ---------- wayback availability ----------

@pytest.mark.asyncio
@respx.mock
async def test_wayback_availability_normalizes():
    payload = {
        "url": "https://example.com",
        "archived_snapshots": {
            "closest": {
                "available": True,
                "url": "https://web.archive.org/web/20230101000000/https://example.com/",
                "timestamp": "20230101000000",
                "status": "200",
            }
        },
    }
    respx.get(AVAIL_ENDPOINT).mock(return_value=httpx.Response(200, json=payload))
    out = await WaybackAvailabilityProvider().lookup("https://example.com")
    assert len(out) == 1
    r = out[0]
    assert r.provider == "wayback_availability"
    assert r.source_type == SourceType.ARCHIVE
    assert r.url.startswith("https://web.archive.org/web/")
    assert "timestamp=20230101000000" in r.snippet
    assert r.raw["legal_basis"] == "public_archive"
    assert r.raw["query_used"] == "https://example.com"


@pytest.mark.asyncio
@respx.mock
async def test_wayback_availability_no_snapshot_returns_empty():
    respx.get(AVAIL_ENDPOINT).mock(
        return_value=httpx.Response(200, json={"url": "https://x", "archived_snapshots": {}})
    )
    out = await WaybackAvailabilityProvider().lookup("https://x")
    assert out == []


@pytest.mark.asyncio
async def test_wayback_availability_skips_freetext_query():
    """A free-text query like 'Anna Lifshitz' is not a URL — return empty
    rather than make a useless API call."""
    out = await WaybackAvailabilityProvider().lookup("Anna Lifshitz")
    assert out == []


# ---------- wayback CDX ----------

@pytest.mark.asyncio
@respx.mock
async def test_wayback_cdx_normalizes():
    # CDX returns a header row + value rows; field order matches our `fl=` param.
    payload = [
        ["timestamp", "original", "statuscode", "mimetype", "digest"],
        ["20230501120000", "https://example.com/page", "200", "text/html", "ABC123"],
        ["20230701120000", "https://example.com/other", "200", "text/html", "DEF456"],
    ]
    respx.get(CDX_ENDPOINT).mock(return_value=httpx.Response(200, json=payload))
    out = await WaybackArchiveProvider().lookup("https://example.com/*", limit=5)
    assert len(out) == 2
    first = out[0]
    assert first.provider == "wayback_cdx"
    assert first.source_type == SourceType.ARCHIVE
    assert first.url == "https://web.archive.org/web/20230501120000/https://example.com/page"
    assert first.raw["statuscode"] == "200"
    assert first.raw["digest"] == "ABC123"
    assert first.raw["legal_basis"] == "public_archive"


@pytest.mark.asyncio
@respx.mock
async def test_wayback_cdx_empty_list():
    respx.get(CDX_ENDPOINT).mock(return_value=httpx.Response(200, json=[]))
    assert await WaybackArchiveProvider().lookup("https://nothing.example/*") == []


# ---------- common crawl ----------

@pytest.mark.asyncio
@respx.mock
async def test_common_crawl_normalizes():
    collinfo = [{"id": "CC-MAIN-2024-30"}, {"id": "CC-MAIN-2024-26"}]
    respx.get(COLLINFO_ENDPOINT).mock(return_value=httpx.Response(200, json=collinfo))

    cc_lines = (
        '{"url":"https://example.com/a","timestamp":"20240715000000","status":"200","mime":"text/html","digest":"D1","filename":"crawl-data/CC-MAIN-2024-30/segments/x.warc.gz","offset":"0","length":"1024"}\n'
        '{"url":"https://example.com/b","timestamp":"20240716000000","status":"200","mime":"text/html","digest":"D2","filename":"crawl-data/CC-MAIN-2024-30/segments/y.warc.gz","offset":"0","length":"2048"}'
    )
    respx.get("https://index.commoncrawl.org/CC-MAIN-2024-30-index").mock(
        return_value=httpx.Response(200, text=cc_lines)
    )
    respx.get("https://index.commoncrawl.org/CC-MAIN-2024-26-index").mock(
        return_value=httpx.Response(404)
    )

    out = await CommonCrawlArchiveProvider().lookup("https://example.com/*", limit=5)
    assert len(out) == 2
    assert all(r.provider == "common_crawl" for r in out)
    assert all(r.source_type == SourceType.ARCHIVE for r in out)
    assert out[0].url == "https://example.com/a"
    assert out[0].raw["index_id"] == "CC-MAIN-2024-30"
    assert out[0].raw["legal_basis"] == "public_archive"


# ---------- archive endpoint ----------

def test_archive_endpoint_returns_only_archive_evidence():
    """POST /v1/search/archive/start/{case_id} returns ARCHIVE evidence
    only. With MOCK_PROVIDERS=true it's the mock provider; with strict
    mode and free-text variants it returns []."""
    case_id = str(get_store().list_cases()[0].id)
    r = client.post(f"/v1/search/archive/start/{case_id}")
    assert r.status_code == 200
    items = r.json()
    for it in items:
        assert it["source_type"] == "archive"
