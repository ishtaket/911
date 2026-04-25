"""Brave Search Web API provider.

Strict mode (no silent fallback):
  - If `api_key` is missing → raises `ProviderNotConfigured`.
  - On HTTP 429              → raises `RateLimitError`.
  - On other transport errors → re-raises so the orchestrator audit-logs it.

Mock fallback for development is provided by `MockWebSearchProvider`, which
the registry only includes when `MOCK_PROVIDERS=true`.

API docs: https://api-dashboard.search.brave.com/app/documentation/web-search
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

BRAVE_ENDPOINT = "https://api.search.brave.com/res/v1/web/search"
BRAVE_TIMEOUT_SEC = 10.0


class BraveWebSearchProvider(WebSearchProvider):
    name = "brave_web_search"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            raise ProviderNotConfigured(
                "BRAVE_SEARCH_API_KEY is not set. Configure it in the backend env."
            )

        params = {
            "q": query,
            "count": str(limit),
            "search_lang": language,
            "country": "IL",
            "safesearch": "moderate",
        }
        headers = {
            "Accept": "application/json",
            "Accept-Encoding": "gzip",
            "X-Subscription-Token": self.api_key,
        }

        async with httpx.AsyncClient(timeout=BRAVE_TIMEOUT_SEC) as client:
            resp = await client.get(BRAVE_ENDPOINT, params=params, headers=headers)
            if resp.status_code == 429:
                raise RateLimitError(f"Brave Search rate-limited (429). Retry-After={resp.headers.get('Retry-After')}")
            resp.raise_for_status()
            data = resp.json()

        fetched_at = datetime.now(timezone.utc)
        out: list[ProviderResult] = []
        items = (data.get("web", {}).get("results") or [])[:limit]
        for item in items:
            url = item.get("url")
            title = item.get("title")
            snippet = item.get("description")
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
                        "brave_item": item,
                    },
                    confidence=0.6,
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return out
