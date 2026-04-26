"""Google Custom Search JSON API (Programmable Search) provider.

Strict mode (no silent fallback):
  - Missing api_key OR cse_id → raises `ProviderNotConfigured`.
  - HTTP 403                  → raises `ProviderNotConfigured` with a clear
                                message: the Custom Search JSON API may not
                                be enabled for this project, or Google has
                                closed it to new customers. Either way, this
                                is operator action — not a runtime retry.
  - HTTP 429                  → raises `RateLimitError`.
  - Other transport errors    → re-raised so the orchestrator audit-logs them.

Mock fallback for development is provided by `MockWebSearchProvider`, which
the registry only includes when `MOCK_PROVIDERS=true`.

API docs: https://developers.google.com/custom-search/v1/using_rest
"""
from __future__ import annotations

import hashlib
from datetime import datetime, timezone

import httpx

from app.providers.base import (
    ProviderNotConfigured,
    RateLimitError,
    WebSearchProvider,
)
from app.schemas.provider_result import ProviderResult, SourceType

CSE_ENDPOINT = "https://www.googleapis.com/customsearch/v1"
CSE_TIMEOUT_SEC = 10.0


class GoogleCseWebSearchProvider(WebSearchProvider):
    name = "google_cse"

    def __init__(self, api_key: str | None, cse_id: str | None = None) -> None:
        self.api_key = api_key
        self.cse_id = cse_id

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            raise ProviderNotConfigured(
                "GOOGLE_CSE_API_KEY is not set. Configure a Google Custom "
                "Search JSON API key in the backend env."
            )
        if not self.cse_id:
            raise ProviderNotConfigured(
                "GOOGLE_CSE_ENGINE_ID is not set. Create a Programmable "
                "Search Engine and set its cx value in the backend env."
            )

        params = {
            "key": self.api_key,
            "cx": self.cse_id,
            "q": query,
            "num": str(min(limit, 10)),
            "hl": language,
            "safe": "active",
        }
        async with httpx.AsyncClient(timeout=CSE_TIMEOUT_SEC) as client:
            resp = await client.get(CSE_ENDPOINT, params=params)
            if resp.status_code == 403:
                raise ProviderNotConfigured(
                    "Google Custom Search API returned 403. This project "
                    "may not have access to the Custom Search JSON API "
                    "(API not enabled in Google Cloud, or the API is "
                    "closed to new customers). Verify the key + engine "
                    "ID and that 'Custom Search API' is enabled, or "
                    "switch to another web-search provider."
                )
            if resp.status_code == 429:
                raise RateLimitError(
                    f"Google Custom Search rate-limited (429). "
                    f"Retry-After={resp.headers.get('Retry-After')}"
                )
            resp.raise_for_status()
            data = resp.json()

        fetched_at = datetime.now(timezone.utc)
        out: list[ProviderResult] = []
        items = (data.get("items") or [])[:limit]
        for item in items:
            url = item.get("link")
            title = item.get("title")
            snippet = item.get("snippet")
            content_hash = (
                hashlib.sha256(f"{url}|{title}".encode()).hexdigest() if url else None
            )
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.WEB,
                    title=title,
                    url=url,
                    snippet=snippet,
                    language=language,
                    fetched_at=fetched_at,
                    raw={
                        "query_used": query,
                        "legal_basis": "public",
                        "displayLink": item.get("displayLink"),
                        "formattedUrl": item.get("formattedUrl"),
                        "cacheId": item.get("cacheId"),
                    },
                    confidence=0.60,
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return out
