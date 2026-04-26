"""Search-engine snippet "archive" provider (placeholder).

The intent of this provider is to surface indexed snippets that may
persist after the underlying page is deleted — operators sometimes
recover information from Bing / Google cache or Brave's indexed
snippet that points to a dead URL.

Strict mode: this provider has **no real implementation yet**. The
previous version silently fell back to `MockArchiveProvider`, which
leaked deterministic fake snapshots ("[mock archive] snapshot N for:
…") into the operator's Archive Results screen *even when
MOCK_PROVIDERS=false*. That violated the project rule "no fake
evidence in Backend mode".

Until a real snippet-extraction backend lands here (TODO), the
provider raises `ProviderNotConfigured` so the orchestrator audit-logs
it and the dispatch response surfaces the truth instead of fabricating
results.
"""
from __future__ import annotations

from app.providers.base import ArchiveProvider, ProviderNotConfigured
from app.schemas.provider_result import ProviderResult


class SearchSnippetArchiveProvider(ArchiveProvider):
    name = "search_snippet"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        raise ProviderNotConfigured(
            "Search-snippet archive provider has no real implementation "
            "yet. It used to silently mock; in strict Backend mode it "
            "now raises ProviderNotConfigured. Implement real snippet "
            "extraction (Bing/Google cache, Brave snippet) before "
            "re-enabling."
        )
