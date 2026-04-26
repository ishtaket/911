"""Vertex AI Search (Discovery Engine / Agent Search) provider.

Official Google migration path for the retired Custom Search Site
Restricted JSON API. We use the **`searchLite`** method, which:

  - is API-key-authenticated (no service account, no OAuth),
  - is restricted to **public-website data stores** (exactly the
    workload Site Restricted JSON API used to handle), and
  - is the path Google documents at
    https://cloud.google.com/generative-ai-app-builder/docs/migrate-from-cse.

Endpoint (REST):
    POST https://discoveryengine.googleapis.com/v1/
        projects/{project}/locations/{location}/
        collections/{collection}/engines/{engine}/
        servingConfigs/{serving_config}:searchLite
        ?key={api_key}

Body:
    {"query": "...", "pageSize": N}

Strict-mode behaviour:
  - disabled flag false                  → ProviderNotConfigured
  - any required config missing           → ProviderNotConfigured
  - HTTP 403 PERMISSION_DENIED            → ProviderUnavailable
  - HTTP 404 (engine / SC missing)        → ProviderNotConfigured
  - HTTP 429                              → RateLimitError
  - 200 OK                                → normalized ProviderResult[]

We use raw httpx instead of the `google-cloud-discoveryengine` Python
client to avoid pulling a heavy GCP dep when the API-key path is
sufficient. If the operator later switches to OAuth/service-account
auth, swap in the official client behind the same provider interface.
"""
from __future__ import annotations

import hashlib
from datetime import datetime, timezone

import httpx

from app.providers.base import (
    ProviderNotConfigured,
    ProviderUnavailable,
    RateLimitError,
    WebSearchProvider,
)
from app.schemas.provider_result import ProviderResult, SourceType

DISCOVERY_ENGINE_HOST = "https://discoveryengine.googleapis.com"
VERTEX_AI_SEARCH_TIMEOUT_SEC = 15.0


def _serving_config_path(
    project_id: str,
    location: str,
    collection: str,
    engine_id: str,
    serving_config: str,
) -> str:
    return (
        f"projects/{project_id}"
        f"/locations/{location}"
        f"/collections/{collection}"
        f"/engines/{engine_id}"
        f"/servingConfigs/{serving_config}"
    )


class VertexAiSearchProvider(WebSearchProvider):
    """API-key-authenticated Vertex AI Search over a public-website
    data store. The data store + engine must be created in Google
    Cloud Console; this provider only issues queries."""

    name = "vertex_ai_search"

    def __init__(
        self,
        *,
        enabled: bool,
        project_id: str | None,
        location: str,
        collection: str,
        engine_id: str | None,
        serving_config: str,
        api_key: str | None,
    ) -> None:
        self.enabled = enabled
        self.project_id = project_id
        self.location = location
        self.collection = collection
        self.engine_id = engine_id
        self.serving_config = serving_config
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.enabled:
            raise ProviderNotConfigured(
                "Vertex AI Search is disabled. Set "
                "VERTEX_AI_SEARCH_ENABLED=true and configure "
                "VERTEX_AI_PROJECT_ID, VERTEX_AI_ENGINE_ID, and an "
                "API key (VERTEX_AI_API_KEY or GOOGLE_CSE_API_KEY)."
            )
        missing: list[str] = []
        if not self.project_id:
            missing.append("VERTEX_AI_PROJECT_ID")
        if not self.engine_id:
            missing.append("VERTEX_AI_ENGINE_ID")
        if not self.api_key:
            missing.append("VERTEX_AI_API_KEY (or GOOGLE_CSE_API_KEY)")
        if missing:
            raise ProviderNotConfigured(
                "Vertex AI Search is missing required config: "
                + ", ".join(missing)
                + ". See docs/GOOGLE_SEARCH_PROVIDER_DECISION.md."
            )

        sc_path = _serving_config_path(
            self.project_id, self.location, self.collection,
            self.engine_id, self.serving_config,
        )
        url = f"{DISCOVERY_ENGINE_HOST}/v1/{sc_path}:searchLite"
        params = {"key": self.api_key}
        body = {
            "query": query,
            "pageSize": min(limit, 25),  # docs cap pageSize=25 for websites
        }

        async with httpx.AsyncClient(timeout=VERTEX_AI_SEARCH_TIMEOUT_SEC) as client:
            resp = await client.post(url, params=params, json=body)
            if resp.status_code == 403:
                err_lower = (resp.text or "").lower()
                if (
                    "permission_denied" in err_lower
                    or "does not have access" in err_lower
                    or "does not have the access" in err_lower
                    or "permission denied" in err_lower
                ):
                    raise ProviderUnavailable(
                        "Vertex AI Search returned 403 PERMISSION_DENIED "
                        "for this Google Cloud project. The Discovery "
                        "Engine API may not be enabled, the API key may "
                        "not be authorized for the discoveryengine.* "
                        "endpoints, or the project lacks billing. "
                        "OAuth will not change this — the gate is at "
                        "the project / IAM level."
                    )
                raise ProviderNotConfigured(
                    f"Vertex AI Search returned 403. Body: "
                    f"{(resp.text or '')[:300]}"
                )
            if resp.status_code == 404:
                raise ProviderNotConfigured(
                    "Vertex AI Search returned 404. Verify "
                    "VERTEX_AI_PROJECT_ID, VERTEX_AI_LOCATION, "
                    "VERTEX_AI_ENGINE_ID, VERTEX_AI_SERVING_CONFIG, "
                    "VERTEX_AI_COLLECTION — the data store / engine "
                    "may not exist or the path is mis-spelled."
                )
            if resp.status_code == 429:
                raise RateLimitError(
                    "Vertex AI Search rate-limited (429). "
                    f"Retry-After={resp.headers.get('Retry-After')}"
                )
            resp.raise_for_status()
            data = resp.json()

        fetched_at = datetime.now(timezone.utc)
        out: list[ProviderResult] = []
        for r in (data.get("results") or [])[:limit]:
            doc = r.get("document") or {}
            derived = doc.get("derivedStructData") or {}
            url_v = derived.get("link") or doc.get("uri")
            title = derived.get("title") or doc.get("name")
            snippet = derived.get("snippet") or derived.get("htmlFormattedSnippet")
            doc_id = doc.get("id") or r.get("id") or url_v or ""
            content_hash = (
                hashlib.sha256(f"vertex|{doc_id}".encode()).hexdigest()
                if doc_id else None
            )
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.WEB,
                    title=title,
                    url=url_v,
                    snippet=snippet,
                    language=language,
                    fetched_at=fetched_at,
                    raw={
                        "query_used": query,
                        "legal_basis": "public",
                        "vertex_id": doc_id,
                        "data_store": derived.get("source"),
                        "displayLink": derived.get("displayLink"),
                    },
                    confidence=0.62,
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return out
