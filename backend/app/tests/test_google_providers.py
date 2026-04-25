"""Google providers — YouTube and Knowledge Graph.

Same strict-mode contract as Brave: missing key in strict mode raises
ProviderNotConfigured; mocked HTTP returns normalized Evidence; quota
errors map to RateLimitError; /v1/providers reflects state honestly.
"""
from __future__ import annotations

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app
from app.providers.base import ProviderNotConfigured, RateLimitError
from app.providers.social.youtube import YT_ENDPOINT, YouTubePublicProvider
from app.providers.web_search.google_kg import KG_ENDPOINT, GoogleKnowledgeGraphProvider
from app.schemas.provider_result import SourceType

client = TestClient(create_app())


# ---------- YouTube ----------

@pytest.mark.asyncio
async def test_youtube_no_key_strict_raises():
    p = YouTubePublicProvider(api_key=None)
    with pytest.raises(ProviderNotConfigured):
        await p.search("Anna Lifshitz")


@pytest.mark.asyncio
@respx.mock
async def test_youtube_normalizes_video_and_channel():
    payload = {
        "items": [
            {
                "id": {"kind": "youtube#video", "videoId": "abc123"},
                "snippet": {
                    "title": "Public sighting clip",
                    "description": "Volunteers shared this public clip.",
                    "channelTitle": "RescueIL",
                    "publishedAt": "2026-04-01T10:00:00Z",
                },
            },
            {
                "id": {"kind": "youtube#channel", "channelId": "ch999"},
                "snippet": {
                    "title": "Volunteer rescue channel",
                    "description": "Public IL rescue org.",
                    "channelTitle": "Volunteer rescue channel",
                    "publishedAt": "2024-01-01T10:00:00Z",
                },
            },
        ]
    }
    route = respx.get(YT_ENDPOINT).mock(return_value=httpx.Response(200, json=payload))
    out = await YouTubePublicProvider(api_key="yt_test_key_xxxxxxxxxxxxxxxx").search("Anna L", limit=10)
    assert route.called
    assert len(out) == 2
    assert out[0].provider == "youtube_data_api"
    assert out[0].source_type == SourceType.SOCIAL
    assert out[0].url == "https://www.youtube.com/watch?v=abc123"
    assert out[0].raw["legal_basis"] == "public"
    assert out[0].raw["channel_title"] == "RescueIL"
    assert out[1].url == "https://www.youtube.com/channel/ch999"


@pytest.mark.asyncio
@respx.mock
async def test_youtube_quota_403_raises_rate_limit():
    respx.get(YT_ENDPOINT).mock(return_value=httpx.Response(403, json={"error": "quota"}))
    with pytest.raises(RateLimitError):
        await YouTubePublicProvider(api_key="yt_test").search("x")


# ---------- Knowledge Graph ----------

@pytest.mark.asyncio
async def test_kg_no_key_strict_raises():
    p = GoogleKnowledgeGraphProvider(api_key=None)
    with pytest.raises(ProviderNotConfigured):
        await p.search("Tel Aviv")


@pytest.mark.asyncio
@respx.mock
async def test_kg_normalizes():
    payload = {
        "itemListElement": [
            {
                "resultScore": 9999.0,
                "result": {
                    "@id": "kg:/m/0d6lp",
                    "@type": ["Place", "City"],
                    "name": "Tel Aviv-Yafo",
                    "description": "City in Israel",
                    "detailedDescription": {
                        "articleBody": "Tel Aviv is a major coastal city in Israel.",
                        "url": "https://en.wikipedia.org/wiki/Tel_Aviv",
                    },
                },
            }
        ]
    }
    respx.get(KG_ENDPOINT).mock(return_value=httpx.Response(200, json=payload))
    out = await GoogleKnowledgeGraphProvider(api_key="kg_test_key_xxxxxxxxxxxx").search("Tel Aviv", limit=5)
    assert len(out) == 1
    e = out[0]
    assert e.provider == "google_kg_search"
    assert e.title == "Tel Aviv-Yafo"
    assert e.url == "https://en.wikipedia.org/wiki/Tel_Aviv"
    assert "Tel Aviv is a major" in e.snippet
    assert e.raw["legal_basis"] == "public"
    assert e.raw["kg_id"] == "kg:/m/0d6lp"


# ---------- /v1/providers reflects Google state honestly ----------

def test_providers_strict_no_keys_marks_google_not_configured():
    """Build the registry directly with strict settings (so the global app
    instance's settings cache isn't disturbed)."""
    from app.services.provider_registry_service import list_providers
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "youtube_api_key": None,
        "google_vision_api_key": None,
        "google_application_credentials": None,
        "google_kg_api_key": None,
    })
    rows = {p.provider_id: p for p in list_providers(s).providers}
    for pid in ("youtube_data_api", "google_vision", "google_kg_search"):
        assert rows[pid].state == "not_configured", f"{pid} state={rows[pid].state}"
        assert rows[pid].requires_user_action is True
        assert rows[pid].configured is False


def test_providers_dev_mode_marks_google_mock():
    from app.services.provider_registry_service import list_providers
    s = get_settings().model_copy(update={
        "mock_providers": True,
        "youtube_api_key": None,
        "google_vision_api_key": None,
        "google_application_credentials": None,
        "google_kg_api_key": None,
    })
    rows = {p.provider_id: p for p in list_providers(s).providers}
    for pid in ("youtube_data_api", "google_vision", "google_kg_search"):
        assert rows[pid].state == "mock", f"{pid} state={rows[pid].state}"


def test_providers_no_secrets_returned_at_all():
    """Final guard: even with extra Google rows, secrets never appear."""
    body = client.get("/v1/providers").text
    for forbidden in ("yt_test_key_", "kg_test_key_", "AIza", "X-Subscription-Token"):
        assert forbidden.lower() not in body.lower(), f"leaked: {forbidden}"
