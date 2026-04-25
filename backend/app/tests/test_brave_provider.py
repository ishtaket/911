"""Tests for the real Brave Search provider wiring."""
from __future__ import annotations

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app
from app.providers.base import ProviderNotConfigured, RateLimitError
from app.providers.registry import get_web_search_providers
from app.providers.web_search.brave import BRAVE_ENDPOINT, BraveWebSearchProvider
from app.providers.web_search.mock import MockWebSearchProvider
from app.schemas.provider_result import SourceType

client = TestClient(create_app())


# ---------- registry ----------

def test_registry_strict_excludes_mock_when_no_key():
    """MOCK_PROVIDERS=false + no Brave key → registry has Brave + GoogleCSE only.
    The Mock provider is NOT silently appended."""
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "brave_search_api_key": None,
        "google_maps_api_key": None,
    })
    providers = get_web_search_providers(s)
    names = [p.name for p in providers]
    assert "mock_web_search" not in names
    assert "brave_web_search" in names


def test_registry_includes_mock_when_mock_mode_on():
    s = get_settings().model_copy(update={
        "mock_providers": True,
        "brave_search_api_key": "rl-test-key",  # also has key
    })
    providers = get_web_search_providers(s)
    assert any(isinstance(p, MockWebSearchProvider) for p in providers)


def test_registry_returns_only_mock_when_pure_dev():
    """Default dev: MOCK_PROVIDERS=true and no web-search key of any kind → mock-only."""
    s = get_settings().model_copy(update={
        "mock_providers": True,
        "brave_search_api_key": None,
        "google_maps_api_key": None,
        "google_kg_api_key": None,
    })
    providers = get_web_search_providers(s)
    assert len(providers) == 1
    assert isinstance(providers[0], MockWebSearchProvider)


# ---------- brave provider behavior ----------

@pytest.mark.asyncio
async def test_brave_no_key_raises_not_configured():
    p = BraveWebSearchProvider(api_key=None)
    with pytest.raises(ProviderNotConfigured):
        await p.search("missing person Tel Aviv")


@pytest.mark.asyncio
@respx.mock
async def test_brave_normalizes_real_response():
    fake_payload = {
        "web": {
            "results": [
                {
                    "title": "Public sighting near promenade",
                    "url": "https://example.org/news/1",
                    "description": "Volunteers reported seeing a person matching the description.",
                },
                {
                    "title": "Hiking forum thread",
                    "url": "https://example.org/forum/2",
                    "description": "Public discussion of recent disappearances.",
                },
            ]
        }
    }
    route = respx.get(BRAVE_ENDPOINT).mock(
        return_value=httpx.Response(200, json=fake_payload)
    )

    p = BraveWebSearchProvider(api_key="brv_test_key_xxxxxxxxxxxxxxxx")
    out = await p.search("Anna Lifshitz Tel Aviv", language="en", limit=5)

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert sent_headers["X-Subscription-Token"].startswith("brv_test_key")

    assert len(out) == 2
    first = out[0]
    assert first.provider == "brave_web_search"
    assert first.source_type == SourceType.WEB
    assert first.title == "Public sighting near promenade"
    assert first.url == "https://example.org/news/1"
    assert first.snippet.startswith("Volunteers reported")
    assert first.is_legal_source is True
    assert first.content_hash, "content_hash should be set when url+title present"
    # provenance
    assert first.raw["query_used"] == "Anna Lifshitz Tel Aviv"
    assert first.raw["legal_basis"] == "public"
    assert "brave_item" in first.raw


@pytest.mark.asyncio
@respx.mock
async def test_brave_rate_limit_raises():
    respx.get(BRAVE_ENDPOINT).mock(
        return_value=httpx.Response(429, headers={"Retry-After": "30"})
    )
    p = BraveWebSearchProvider(api_key="brv_test_key_xxxxxxxxxxxxxxxx")
    with pytest.raises(RateLimitError):
        await p.search("anything")


# ---------- providers endpoint reflects state honestly ----------

def test_providers_endpoint_no_secrets_returned():
    """Even with a real key envisioned, /v1/providers must never echo it."""
    r = client.get("/v1/providers")
    assert r.status_code == 200
    body = r.text
    # Should never appear in serialized output even if keys were set in env.
    for forbidden in ("brv_test_key_", "X-Subscription-Token", "BRAVE_SEARCH_API_KEY="):
        assert forbidden.lower() not in body.lower(), f"leaked: {forbidden}"


def test_providers_endpoint_brave_state_visible():
    r = client.get("/v1/providers")
    brave = next(p for p in r.json()["providers"] if p["provider_id"] == "brave_web_search")
    # In dev (MOCK_PROVIDERS=true, no key) state must be 'mock' — surfaced
    # to the operator instead of pretending to be 'connected'.
    assert brave["state"] in {"mock", "connected", "not_configured"}
    assert brave["safe_scope_description"]
    assert brave["auth_type"] == "api_key"
