"""YouTube Data API v3 — public search.

Strict mode: missing YOUTUBE_API_KEY raises ProviderNotConfigured (no
silent mock fallback). Public videos and channel metadata only — no
private account access.

Endpoint: https://www.googleapis.com/youtube/v3/search
Docs:     https://developers.google.com/youtube/v3/docs/search/list
"""
from __future__ import annotations

import hashlib

import httpx

from app.providers.base import (
    ProviderNotConfigured,
    RateLimitError,
    SocialSearchProvider,
)
from app.schemas.provider_result import ProviderResult, SourceType

YT_ENDPOINT = "https://www.googleapis.com/youtube/v3/search"
TIMEOUT_SEC = 10.0


class YouTubePublicProvider(SocialSearchProvider):
    name = "youtube_data_api"
    network = "youtube"

    def __init__(self, api_key: str | None) -> None:
        self.api_key = api_key

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            raise ProviderNotConfigured(
                "YOUTUBE_API_KEY is not set. Configure it in the backend env."
            )

        params = {
            "key": self.api_key,
            "part": "snippet",
            "type": "video,channel",
            "q": query,
            "maxResults": str(min(limit, 25)),
            "relevanceLanguage": language,
            "safeSearch": "moderate",
        }
        async with httpx.AsyncClient(timeout=TIMEOUT_SEC) as client:
            resp = await client.get(YT_ENDPOINT, params=params)
            if resp.status_code in (403, 429):
                # 403 from YouTube usually means quotaExceeded
                raise RateLimitError(f"YouTube Data API quota / rate-limited ({resp.status_code})")
            resp.raise_for_status()
            data = resp.json()

        out: list[ProviderResult] = []
        for item in (data.get("items") or [])[:limit]:
            sn = item.get("snippet") or {}
            id_obj = item.get("id") or {}
            kind = id_obj.get("kind")
            if kind == "youtube#video":
                vid = id_obj.get("videoId")
                url = f"https://www.youtube.com/watch?v={vid}" if vid else None
                ext_id = vid
            elif kind == "youtube#channel":
                ch = id_obj.get("channelId")
                url = f"https://www.youtube.com/channel/{ch}" if ch else None
                ext_id = ch
            else:
                continue
            title = sn.get("title")
            description = sn.get("description")
            content_hash = (
                hashlib.sha256(f"yt|{kind}|{ext_id}".encode()).hexdigest() if ext_id else None
            )
            out.append(
                ProviderResult(
                    provider=self.name,
                    source_type=SourceType.SOCIAL,
                    title=title,
                    url=url,
                    snippet=description,
                    language=language,
                    confidence=0.55,
                    is_legal_source=True,
                    content_hash=content_hash,
                    raw={
                        "query_used": query,
                        "legal_basis": "public",
                        "kind": kind,
                        "external_id": ext_id,
                        "channel_title": sn.get("channelTitle"),
                        "published_at": sn.get("publishedAt"),
                    },
                )
            )
        return out
