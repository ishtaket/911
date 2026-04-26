"""Tests for the Google Custom Search JSON API provider.

Strict-mode contract:
  - missing GOOGLE_CSE_API_KEY  → ProviderNotConfigured
  - missing GOOGLE_CSE_ENGINE_ID → ProviderNotConfigured
  - 200 success                 → normalized WEB ProviderResult items
  - 403                         → ProviderNotConfigured (Custom Search JSON
                                  API may not be enabled / closed to new
                                  customers — operator action, not retry)
  - 429                         → RateLimitError

Also asserts registry ordering: google_cse must be the FIRST web provider.
"""
from __future__ import annotations

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import create_app
from app.providers.base import (
    ProviderNotConfigured,
    ProviderUnavailable,
    RateLimitError,
)
from app.providers.registry import get_web_search_providers
from app.providers.web_search.google_cse import (
    CSE_ENDPOINT,
    GoogleCseWebSearchProvider,
)
from app.schemas.provider_result import SourceType

client = TestClient(create_app())


# ---------- provider behavior ----------

@pytest.mark.asyncio
async def test_cse_no_api_key_raises_not_configured():
    p = GoogleCseWebSearchProvider(api_key=None, cse_id="abc")
    with pytest.raises(ProviderNotConfigured) as exc:
        await p.search("missing person Tel Aviv")
    assert "GOOGLE_CSE_API_KEY" in str(exc.value)


@pytest.mark.asyncio
async def test_cse_no_engine_id_raises_not_configured():
    p = GoogleCseWebSearchProvider(api_key="cse_test_key_xxxxxxxxxxxxxxxx", cse_id=None)
    with pytest.raises(ProviderNotConfigured) as exc:
        await p.search("missing person Tel Aviv")
    assert "GOOGLE_CSE_ENGINE_ID" in str(exc.value)


@pytest.mark.asyncio
@respx.mock
async def test_cse_normalizes_real_response():
    fake_payload = {
        "items": [
            {
                "title": "Public sighting near promenade",
                "link": "https://example.org/news/1",
                "snippet": "Volunteers reported seeing a person matching the description.",
                "displayLink": "example.org",
                "formattedUrl": "example.org/news/1",
                "cacheId": "abc123",
            },
            {
                "title": "Hiking forum thread",
                "link": "https://example.org/forum/2",
                "snippet": "Public discussion of recent disappearances.",
                "displayLink": "example.org",
            },
        ]
    }
    route = respx.get(CSE_ENDPOINT).mock(
        return_value=httpx.Response(200, json=fake_payload)
    )

    p = GoogleCseWebSearchProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cse_engine_id_xxxxxxxx",
    )
    out = await p.search("Anna Lifshitz Tel Aviv", language="en", limit=5)

    assert route.called
    sent_url = str(route.calls[0].request.url)
    assert "key=cse_test_key_" in sent_url
    assert "cx=cse_engine_id_" in sent_url

    assert len(out) == 2
    first = out[0]
    assert first.provider == "google_cse"
    assert first.source_type == SourceType.WEB
    assert first.title == "Public sighting near promenade"
    assert first.url == "https://example.org/news/1"
    assert first.snippet.startswith("Volunteers reported")
    assert first.is_legal_source is True
    assert first.content_hash, "content_hash should be set when url+title present"
    # provenance
    assert first.raw["query_used"] == "Anna Lifshitz Tel Aviv"
    assert first.raw["legal_basis"] == "public"
    assert first.raw["displayLink"] == "example.org"
    assert first.raw["cacheId"] == "abc123"


@pytest.mark.asyncio
@respx.mock
async def test_cse_403_generic_raises_not_configured_with_clear_message():
    """Generic 403 (API not enabled, key not whitelisted, etc.) is
    operator-fixable, so it surfaces as ProviderNotConfigured."""
    respx.get(CSE_ENDPOINT).mock(
        return_value=httpx.Response(403, json={"error": {"code": 403, "message": "Forbidden"}})
    )
    p = GoogleCseWebSearchProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cse_engine_id_xxxxxxxx",
    )
    with pytest.raises(ProviderNotConfigured) as exc:
        await p.search("anything")
    msg = str(exc.value).lower()
    assert "403" in msg
    assert "custom search" in msg


@pytest.mark.asyncio
@respx.mock
async def test_cse_403_permission_denied_raises_unavailable():
    """The specific Google project-level denial:
        403 PERMISSION_DENIED
        "This project does not have the access to Custom Search JSON API."
    must surface as ProviderUnavailable (NOT ProviderNotConfigured),
    because it is NOT operator-fixable via local config: OAuth and key
    rotation cannot grant access at the project level."""
    body = {
        "error": {
            "code": 403,
            "status": "PERMISSION_DENIED",
            "message": "This project does not have the access to Custom Search JSON API.",
        }
    }
    respx.get(CSE_ENDPOINT).mock(return_value=httpx.Response(403, json=body))
    p = GoogleCseWebSearchProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cse_engine_id_xxxxxxxx",
    )
    with pytest.raises(ProviderUnavailable) as exc:
        await p.search("anything")
    msg = str(exc.value).lower()
    assert "unavailable" in msg
    assert "oauth" in msg, "operator must be told that OAuth does not fix this"


@pytest.mark.asyncio
@respx.mock
async def test_cse_429_raises_rate_limit():
    respx.get(CSE_ENDPOINT).mock(
        return_value=httpx.Response(429, headers={"Retry-After": "30"})
    )
    p = GoogleCseWebSearchProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cse_engine_id_xxxxxxxx",
    )
    with pytest.raises(RateLimitError):
        await p.search("anything")


# ---------- registry ordering ----------

def test_web_provider_order_starts_with_google_cse():
    """When real keys are present, the registry must order providers
    google_cse → google_kg_search → brave_web_search (operator-visible
    priority for the web channel)."""
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "google_cse_api_key": "cse_test_key_xxxxxxxxxxxxxxxx",
        "google_cse_engine_id": "cse_engine_id_xxxxxxxx",
        "google_kg_api_key": "kg_test_key_xxxxxxxxxxxx",
        "brave_search_api_key": "brv_test_key_xxxxxxxxxxxxxxxx",
    })
    providers = get_web_search_providers(s)
    names = [p.name for p in providers]
    assert names[:3] == ["google_cse", "google_kg_search", "brave_web_search"], names


def test_web_provider_order_holds_when_keys_missing():
    """Even when keys are missing the order must still start with
    google_cse — the wrapped provider will raise ProviderNotConfigured at
    call-time, not at registration time."""
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "google_cse_api_key": None,
        "google_cse_engine_id": None,
        "google_kg_api_key": None,
        "brave_search_api_key": None,
    })
    providers = get_web_search_providers(s)
    names = [p.name for p in providers]
    assert names[0] == "google_cse"


# ---------- /v1/providers reflects CSE state honestly ----------

def test_providers_endpoint_cse_state_visible():
    """In dev (MOCK_PROVIDERS=true, no key) state must be 'mock' or
    'not_configured' — surfaced honestly to the operator."""
    r = client.get("/v1/providers")
    cse = next(p for p in r.json()["providers"] if p["provider_id"] == "google_cse")
    assert cse["state"] in {"mock", "connected", "not_configured"}
    assert cse["display_name"] == "Google Custom Search JSON API"
    assert "Custom Search" in cse["safe_scope_description"]
    assert cse["auth_type"] == "api_key"


def test_providers_endpoint_no_cse_secrets_returned():
    r = client.get("/v1/providers")
    body = r.text
    for forbidden in ("cse_test_key_", "cse_engine_id_", "GOOGLE_CSE_API_KEY="):
        assert forbidden.lower() not in body.lower(), f"leaked: {forbidden}"
