"""Brave Search provider (placeholder, falls back to mock if no API key)."""
from __future__ import annotations

import hashlib

import httpx

from app.providers.base import WebSearchProvider
from app.providers.web_search.mock import MockWebSearchProvider
from app.schemas.provider_result import ProviderResult, SourceType

BRAVE_ENDPOINT = "https://api.search.brave.com/res/v1/web/search"


class BraveWebSearchProvider(WebSearchProvider):
    name = "brave_web_search"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key
        self._fallback = MockWebSearchProvider()

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            return await self._fallback.search(query, language=language, limit=limit)

        params = {"q": query, "count": str(limit), "search_lang": language, "country": "IL"}
        headers = {"Accept": "application/json", "X-Subscription-Token": self.api_key}
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(BRAVE_ENDPOINT, params=params, headers=headers)
                resp.raise_for_status()
                data = resp.json()
        except Exception:
            return await self._fallback.search(query, language=language, limit=limit)

        out: list[ProviderResult] = []
        for item in (data.get("web", {}).get("results", []) or [])[:limit]:
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
                    raw=item,
                    confidence=0.6,
                    is_legal_source=True,
                    content_hash=content_hash,
                )
            )
        return out
