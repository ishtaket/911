"""Public-mirror archive provider (placeholder).

Intent: query a curated allow-list of *real* public re-hosters
(e.g., archive.today, library mirrors). Not yet implemented.

Strict mode: like other unimplemented archive stubs, this provider
**raises `ProviderNotConfigured`** rather than silently falling back
to `MockArchiveProvider`. It was leaking "[mock archive] snapshot"
text into the operator's Archive Results screen — which violates the
"no fake evidence in Backend mode" rule.

Note: this provider is not currently included in
`get_archive_providers()`; the strict-mode behaviour here is a guard
in case a future change re-adds it before a real implementation lands.
"""
from __future__ import annotations

from app.providers.base import ArchiveProvider, ProviderNotConfigured
from app.schemas.provider_result import ProviderResult


class PublicMirrorArchiveProvider(ArchiveProvider):
    name = "public_mirror"

    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        raise ProviderNotConfigured(
            "Public-mirror archive provider has no real implementation "
            "yet. It used to silently mock; in strict Backend mode it "
            "now raises ProviderNotConfigured. Implement a curated "
            "allow-list query before re-enabling."
        )
