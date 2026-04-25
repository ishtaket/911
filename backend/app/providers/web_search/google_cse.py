"""Google Custom Search Engine provider.

Strict mode: missing api_key raises ProviderNotConfigured. The
orchestrator audit-logs and surfaces this as a per-provider state, so
the operator UI can show "not_configured" instead of fabricating fake
results via a silent mock fallback.
"""
from __future__ import annotations

from app.providers.base import ProviderNotConfigured, WebSearchProvider
from app.schemas.provider_result import ProviderResult


class GoogleCseWebSearchProvider(WebSearchProvider):
    name = "google_cse"

    def __init__(self, api_key: str | None, cse_id: str | None = None) -> None:
        self.api_key = api_key
        self.cse_id = cse_id

    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        if not self.api_key:
            raise ProviderNotConfigured(
                "GOOGLE_CSE/GOOGLE_MAPS_API_KEY is not set. Configure a real "
                "Google Custom Search key in the backend env to enable this "
                "provider, or enable MOCK_PROVIDERS=true for dev."
            )
        # TODO: implement real Google CSE call when keys are provided.
        raise ProviderNotConfigured(
            "Google CSE real-API call is not implemented yet; treating as "
            "not_configured rather than silently mocking."
        )
