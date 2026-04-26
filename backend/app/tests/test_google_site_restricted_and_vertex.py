"""Tests for the Google Site Restricted JSON API stub (deprecated) and
the Vertex AI Search `searchLite` provider (the official migration
path)."""
from __future__ import annotations

import httpx
import pytest
import respx

from app.config import get_settings
from app.providers.base import (
    ProviderNotConfigured,
    ProviderUnavailable,
    RateLimitError,
)
from app.providers.web_search.google_cse_site_restricted import (
    CSE_SITE_RESTRICTED_ENDPOINT,
    GoogleCseSiteRestrictedProvider,
)
from app.providers.web_search.vertex_ai_search import (
    DISCOVERY_ENGINE_HOST,
    VertexAiSearchProvider,
)
from app.schemas.provider_result import SourceType


# ---------- Site Restricted (deprecated 2025-01-08) ----------

@pytest.mark.asyncio
async def test_site_restricted_disabled_by_default_raises_unavailable():
    """When GOOGLE_CSE_SITE_RESTRICTED_ENABLED=false the provider must
    NEVER hit the dead endpoint — it raises ProviderUnavailable with
    the deprecation date and the migration pointer to Vertex AI Search."""
    p = GoogleCseSiteRestrictedProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cse_engine_id_xxxxxxxx",
        enabled=False,
    )
    with pytest.raises(ProviderUnavailable) as exc:
        await p.search("anything")
    msg = str(exc.value).lower()
    assert "2025-01-08" in str(exc.value)
    assert "vertex" in msg, "must point operators to vertex_ai_search migration"


@pytest.mark.asyncio
async def test_site_restricted_enabled_but_no_key_raises_not_configured():
    p = GoogleCseSiteRestrictedProvider(api_key=None, cse_id="cx", enabled=True)
    with pytest.raises(ProviderNotConfigured):
        await p.search("anything")


@pytest.mark.asyncio
@respx.mock
async def test_site_restricted_enabled_403_permission_denied_raises_unavailable():
    """If Google has restored access for the operator's Cloud project
    (enable flag flipped on) but still returns PERMISSION_DENIED at
    runtime, surface as Unavailable with the migration pointer."""
    body = {
        "error": {
            "code": 403,
            "status": "PERMISSION_DENIED",
            "message": "This project does not have the access to Custom Search JSON API.",
        }
    }
    respx.get(CSE_SITE_RESTRICTED_ENDPOINT).mock(
        return_value=httpx.Response(403, json=body)
    )
    p = GoogleCseSiteRestrictedProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cx",
        enabled=True,
    )
    with pytest.raises(ProviderUnavailable):
        await p.search("anything")


@pytest.mark.asyncio
@respx.mock
async def test_site_restricted_enabled_404_410_raises_unavailable():
    """A 404 / 410 from the dead endpoint is the strongest possible
    confirmation of retirement — surface Unavailable, not 'error'."""
    respx.get(CSE_SITE_RESTRICTED_ENDPOINT).mock(
        return_value=httpx.Response(410)
    )
    p = GoogleCseSiteRestrictedProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cx",
        enabled=True,
    )
    with pytest.raises(ProviderUnavailable):
        await p.search("anything")


@pytest.mark.asyncio
@respx.mock
async def test_site_restricted_enabled_200_normalizes():
    """If Google ever restores Site Restricted for the project, the
    provider normalizes results into WEB ProviderResult, same as CSE."""
    payload = {
        "items": [
            {
                "title": "Public org page",
                "link": "https://example.org/about",
                "snippet": "Public Israel rescue org page.",
                "displayLink": "example.org",
                "formattedUrl": "example.org/about",
            }
        ]
    }
    respx.get(CSE_SITE_RESTRICTED_ENDPOINT).mock(
        return_value=httpx.Response(200, json=payload)
    )
    p = GoogleCseSiteRestrictedProvider(
        api_key="cse_test_key_xxxxxxxxxxxxxxxx",
        cse_id="cx",
        enabled=True,
    )
    out = await p.search("rescue org Israel")
    assert len(out) == 1
    assert out[0].provider == "google_cse_site_restricted"
    assert out[0].source_type == SourceType.WEB
    assert out[0].url == "https://example.org/about"
    assert out[0].raw["legal_basis"] == "public"


# ---------- Vertex AI Search searchLite ----------

def _vx(api_key: str | None = "vx_key_xxxxxxxxxxxxxx",
        project: str | None = "p", engine: str | None = "e",
        enabled: bool = True) -> VertexAiSearchProvider:
    return VertexAiSearchProvider(
        enabled=enabled,
        project_id=project,
        location="global",
        collection="default_collection",
        engine_id=engine,
        serving_config="default_search",
        api_key=api_key,
    )


@pytest.mark.asyncio
async def test_vertex_disabled_raises_not_configured():
    with pytest.raises(ProviderNotConfigured):
        await _vx(enabled=False).search("anything")


@pytest.mark.asyncio
async def test_vertex_missing_project_raises_not_configured():
    with pytest.raises(ProviderNotConfigured) as exc:
        await _vx(project=None).search("x")
    assert "VERTEX_AI_PROJECT_ID" in str(exc.value)


@pytest.mark.asyncio
async def test_vertex_missing_engine_raises_not_configured():
    with pytest.raises(ProviderNotConfigured) as exc:
        await _vx(engine=None).search("x")
    assert "VERTEX_AI_ENGINE_ID" in str(exc.value)


@pytest.mark.asyncio
async def test_vertex_missing_api_key_raises_not_configured():
    with pytest.raises(ProviderNotConfigured) as exc:
        await _vx(api_key=None).search("x")
    assert "API_KEY" in str(exc.value).upper()


def _expected_searchlite_url() -> str:
    return (
        f"{DISCOVERY_ENGINE_HOST}/v1/projects/p/locations/global"
        "/collections/default_collection/engines/e"
        "/servingConfigs/default_search:searchLite"
    )


@pytest.mark.asyncio
@respx.mock
async def test_vertex_403_permission_denied_raises_unavailable():
    body = {
        "error": {
            "code": 403,
            "status": "PERMISSION_DENIED",
            "message": "Permission denied on resource project.",
        }
    }
    respx.post(_expected_searchlite_url()).mock(
        return_value=httpx.Response(403, json=body)
    )
    with pytest.raises(ProviderUnavailable) as exc:
        await _vx().search("query")
    assert "OAuth" in str(exc.value), "must explain OAuth doesn't fix this"


@pytest.mark.asyncio
@respx.mock
async def test_vertex_404_raises_not_configured_with_path_hint():
    respx.post(_expected_searchlite_url()).mock(
        return_value=httpx.Response(404, json={"error": {"code": 404}})
    )
    with pytest.raises(ProviderNotConfigured) as exc:
        await _vx().search("query")
    msg = str(exc.value)
    assert "VERTEX_AI_ENGINE_ID" in msg or "VERTEX_AI_PROJECT_ID" in msg


@pytest.mark.asyncio
@respx.mock
async def test_vertex_429_raises_rate_limit():
    respx.post(_expected_searchlite_url()).mock(
        return_value=httpx.Response(429, headers={"Retry-After": "30"})
    )
    with pytest.raises(RateLimitError):
        await _vx().search("query")


@pytest.mark.asyncio
@respx.mock
async def test_vertex_200_normalizes_results():
    payload = {
        "results": [
            {
                "id": "doc-1",
                "document": {
                    "id": "doc-1",
                    "uri": "https://example.org/news/1",
                    "derivedStructData": {
                        "link": "https://example.org/news/1",
                        "title": "Public sighting near promenade",
                        "snippet": "Volunteers reported a public sighting.",
                        "displayLink": "example.org",
                    },
                },
            }
        ]
    }
    route = respx.post(_expected_searchlite_url()).mock(
        return_value=httpx.Response(200, json=payload)
    )
    out = await _vx().search("Anna Tel Aviv", language="en", limit=5)
    assert route.called
    # body must include query and pageSize
    body = route.calls[0].request.content.decode()
    assert "Anna Tel Aviv" in body
    assert "pageSize" in body
    # url must include the api key as ?key=
    assert "key=vx_key_" in str(route.calls[0].request.url)
    assert len(out) == 1
    e = out[0]
    assert e.provider == "vertex_ai_search"
    assert e.source_type == SourceType.WEB
    assert e.url == "https://example.org/news/1"
    assert e.raw["legal_basis"] == "public"
    assert e.raw["vertex_id"] == "doc-1"
