"""Google Knowledge Graph Search API — public entity lookup.

Strict mode: missing GOOGLE_KG_API_KEY raises ProviderNotConfigured.
This is **entity lookup only**, NOT a web search engine — it returns
people / places / organizations from Google's public KG.

Endpoint: https://kgsearch.googleapis.com/v1/entities:search
Docs:     https://developers.google.com/knowledge-graph
"""
from __future__ import annotations

import hashlib

import httpx

from app.providers.base import (
    ProviderNotConfigured,
    RateLimitError,
    WebSearchProvider,
)
from app.schemas.provider_result import ProviderResult, SourceType

KG_ENDPOINT = "https://kgsearch.googleapis.com/v1/entities:search"
TIMEOUT_SEC = 10.0


class GoogleKnowledgeGraphProvider(WebSearchProvider):
    name = "google_kg_search"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            raise ProviderNotConfigured(
                "GOOGLE_KG_API_KEY is not set. Configure it in the backend env."
            )

        params = {
            "key": self.api_key,
            "query": query,
            "limit": str(min(limit, 20)),
            "languages": language,
        }
        async with httpx.AsyncClient(timeout=TIMEOUT_SEC) as client:
            resp = await client.get(KG_ENDPOINT, params=params)
            if resp.status_code in (403, 429):
                raise RateLimitError(f"Google KG quota / rate-limited ({resp.status_code})")
            resp.raise_for_status()
            data = resp.json()

        out: list[ProviderResult] = []
        for el in (data.get("itemListElement") or [])[:limit]:
            r = (el.get("result") or {})
            name = r.get("name")
            description = r.get("description")
            detailed = r.get("detailedDescription") or {}
            article = detailed.get("articleBody")
            url = detailed.get("url") or r.get("url")
            kg_id = r.get("@id")
            content_hash = (
                hashlib.sha256(f"kg|{kg_id}".encode()).hexdigest() if kg_id else None
            )
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.WEB,
                    title=name,
                    url=url,
                    snippet=article or description,
                    language=language,
                    confidence=0.50,
                    is_legal_source=True,
                    content_hash=content_hash,
                    raw={
                        "query_used": query,
                        "legal_basis": "public",
                        "kg_id": kg_id,
                        "types": r.get("@type"),
                        "result_score": el.get("resultScore"),
                    },
                )
            )
        return out
