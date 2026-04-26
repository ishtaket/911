"""Google Custom Search Site Restricted JSON API provider.

**Status: retired by Google on 2025-01-08.**

Per the official documentation
(https://developers.google.com/custom-search/v1/site_restricted_api):

    "The Custom Search Site Restricted JSON API endpoints will cease to
     serve traffic on January 8, 2025."

The deprecation notice further directs operators to:

    "migrate to Google Cloud's Vertex AI Search."

This provider therefore does NOT issue an HTTP request to the dead
endpoint at https://www.googleapis.com/customsearch/v1/siterestrict —
that would only generate a network error indistinguishable from
transient failures and waste an audit slot. Instead it raises
`ProviderUnavailable` immediately with a clear migration message.

The provider remains in the registry (rather than being deleted)
because:
  - Some operators may still be served by Google during a grace
    period; the `GOOGLE_CSE_SITE_RESTRICTED_ENABLED` flag lets them
    re-enable a real call path if Google's stance changes.
  - Provider visibility lets the operator UI surface "this Google
    path is gone, use Vertex AI Search" instead of silently dropping
    a search channel.

If `GOOGLE_CSE_SITE_RESTRICTED_ENABLED` is ever flipped to true (e.g.,
Google reverses the deprecation), implement the real HTTPS call here
following the same shape as the standard CSE provider.
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

CSE_SITE_RESTRICTED_ENDPOINT = (
    "https://www.googleapis.com/customsearch/v1/siterestrict"
)
CSE_SITE_RESTRICTED_TIMEOUT_SEC = 10.0
DEPRECATION_DATE = "2025-01-08"


class GoogleCseSiteRestrictedProvider(WebSearchProvider):
    """API-key-authenticated site-restricted Google search.

    The wrapped engine (cx) must be a Programmable Search Engine that:
      - searches 10 or fewer specific sites,
      - has "Search the entire web" disabled, and
      - uses no global TLD patterns.

    See https://developers.google.com/custom-search/v1/site_restricted_api.
    """

    name = "google_cse_site_restricted"

    def __init__(
        self,
        api_key: str | None,
        cse_id: str | None,
        enabled: bool = False,
    ) -> None:
        self.api_key = api_key
        self.cse_id = cse_id
        self.enabled = enabled

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        # The honest answer per Google's published deprecation notice.
        # If the operator has explicitly flipped the enable flag, we
        # attempt the call so they can verify Google's current behaviour
        # for their project.
        if not self.enabled:
            raise ProviderUnavailable(
                f"Google Custom Search Site Restricted JSON API was "
                f"retired by Google on {DEPRECATION_DATE} "
                f"(https://developers.google.com/custom-search/v1/"
                f"site_restricted_api). Migrate to Vertex AI Search "
                f"(provider_id=vertex_ai_search). Set "
                f"GOOGLE_CSE_SITE_RESTRICTED_ENABLED=true only if "
                f"Google has restored access for your Cloud project."
            )
        if not self.api_key:
            raise ProviderNotConfigured(
                "GOOGLE_CSE_API_KEY is not set. Configure a Google "
                "Custom Search JSON API key in the backend env."
            )
        if not self.cse_id:
            raise ProviderNotConfigured(
                "GOOGLE_CSE_ENGINE_ID is not set. Create a Programmable "
                "Search Engine with 10-or-fewer-sites + 'Search entire "
                "web' OFF, and set its cx value in the backend env."
            )

        params = {
            "key": self.api_key,
            "cx": self.cse_id,
            "q": query,
            "num": str(min(limit, 10)),
            "hl": language,
            "safe": "active",
        }
        async with httpx.AsyncClient(timeout=CSE_SITE_RESTRICTED_TIMEOUT_SEC) as client:
            resp = await client.get(CSE_SITE_RESTRICTED_ENDPOINT, params=params)
            if resp.status_code == 403:
                err_lower = (resp.text or "").lower()
                if (
                    "permission_denied" in err_lower
                    or "does not have the access" in err_lower
                    or "does not have access" in err_lower
                ):
                    raise ProviderUnavailable(
                        "Google Custom Search Site Restricted JSON API "
                        "is unavailable for this Google Cloud project. "
                        "OAuth will not fix this. The endpoint was "
                        f"officially retired on {DEPRECATION_DATE}; "
                        "migrate to Vertex AI Search "
                        "(provider_id=vertex_ai_search)."
                    )
                raise ProviderNotConfigured(
                    "Google CSE Site Restricted returned 403. Verify the "
                    "key + engine ID and that 'Custom Search API' is "
                    "enabled, or migrate to Vertex AI Search."
                )
            if resp.status_code in (404, 410):
                # Endpoint really is gone.
                raise ProviderUnavailable(
                    f"Google CSE Site Restricted endpoint returned "
                    f"{resp.status_code} — confirms Google's retirement "
                    f"on {DEPRECATION_DATE}. Migrate to Vertex AI Search."
                )
            if resp.status_code == 429:
                raise RateLimitError(
                    "Google CSE Site Restricted rate-limited (429). "
                    f"Retry-After={resp.headers.get('Retry-After')}"
                )
            resp.raise_for_status()
            data = resp.json()

        fetched_at = datetime.now(timezone.utc)
        out: list[ProviderResult] = []
        for item in (data.get("items") or [])[:limit]:
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
