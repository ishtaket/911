"""Archive strict-mode tests.

Goal: in MOCK_PROVIDERS=false mode the operator must never see
"[mock archive] snapshot" results, because:
  - the previous SearchSnippetArchiveProvider silently fell back to
    MockArchiveProvider, leaking deterministic fake snapshots into
    Backend-mode dispatch results;
  - operators reasonably trust that "Data: Backend" means real upstream
    captures only.

These tests pin the contract:
  - get_archive_providers excludes MockArchiveProvider when
    MOCK_PROVIDERS=false;
  - get_archive_providers includes MockArchiveProvider when
    MOCK_PROVIDERS=true (kept for offline dev);
  - SearchSnippetArchiveProvider raises ProviderNotConfigured (no
    silent mock leak);
  - PublicMirrorArchiveProvider raises ProviderNotConfigured;
  - evidence_service refuses to store mock_*-named ProviderResults in
    strict mode, even if a future regression manages to produce one;
  - a real Wayback 200 capture is still normalized and stored.
"""
from __future__ import annotations

from uuid import uuid4

import httpx
import pytest
import respx

from app.config import get_settings
from app.providers.archive.snippet import SearchSnippetArchiveProvider
from app.providers.archive.mirrors import PublicMirrorArchiveProvider
from app.providers.archive.wayback import CDX_ENDPOINT, WaybackArchiveProvider
from app.providers.base import ProviderNotConfigured
from app.providers.registry import get_archive_providers
from app.schemas.provider_result import ProviderResult, SourceType
from app.services import evidence_service


# ---------- registry gating ----------

def test_archive_registry_excludes_mock_in_strict_mode():
    s = get_settings().model_copy(update={"mock_providers": False})
    providers = get_archive_providers(s)
    names = {p.name for p in providers}
    assert "mock_archive" not in names, names
    # Real public-archive providers must remain.
    assert "wayback_availability" in names
    assert "wayback_cdx" in names
    assert "common_crawl" in names


def test_archive_registry_includes_mock_in_dev_mode():
    s = get_settings().model_copy(update={"mock_providers": True})
    providers = get_archive_providers(s)
    names = {p.name for p in providers}
    assert "mock_archive" in names, names


# ---------- snippet & mirrors stubs raise instead of silent-mocking ----------

@pytest.mark.asyncio
async def test_snippet_provider_raises_not_configured():
    """The snippet stub previously fell back to MockArchiveProvider,
    leaking '[mock archive] snapshot ...' into Backend-mode results.
    It must now raise so the orchestrator audit-logs it and no fake
    evidence is produced."""
    p = SearchSnippetArchiveProvider()
    with pytest.raises(ProviderNotConfigured):
        await p.lookup("https://example.org/missing-person")


@pytest.mark.asyncio
async def test_mirrors_provider_raises_not_configured():
    p = PublicMirrorArchiveProvider()
    with pytest.raises(ProviderNotConfigured):
        await p.lookup("https://example.org/missing-person")


# ---------- evidence_service strict-mode guard ----------

def _fake_case_id():
    return uuid4()


def _mock_archive_result() -> ProviderResult:
    return ProviderResult(
        provider="mock_archive",
        source_type=SourceType.ARCHIVE,
        title="[mock archive] snapshot 1 for: x",
        url="https://example.org/archive/mock/0",
        snippet="Mock archive snapshot — public indexed mirror.",
        confidence=0.25,
        is_legal_source=True,
        content_hash="deadbeef" * 8,
    )


def _real_wayback_result() -> ProviderResult:
    return ProviderResult(
        provider="wayback_cdx",
        source_type=SourceType.ARCHIVE,
        title="Wayback capture",
        url="https://web.archive.org/web/2024*/https://example.org/news/1",
        snippet="Public Wayback CDX capture.",
        confidence=0.55,
        is_legal_source=True,
        content_hash="cafef00d" * 8,
    )


def test_evidence_service_drops_mock_named_provider_in_strict_mode(monkeypatch):
    """Belt-and-suspenders: even if a future regression yields a
    ProviderResult with provider='mock_archive' while MOCK_PROVIDERS=
    false, normalize_and_store must NOT store it. Operators trust
    Data: Backend to mean real upstream captures."""
    s = get_settings().model_copy(update={"mock_providers": False})
    monkeypatch.setattr(evidence_service, "get_settings", lambda: s)
    case_id = _fake_case_id()
    stored, deduped = evidence_service.normalize_and_store_with_stats(
        case_id, [_mock_archive_result(), _real_wayback_result()]
    )
    names = [e.provider for e in stored]
    assert "mock_archive" not in names, stored
    assert "wayback_cdx" in names, stored


def test_evidence_service_keeps_mock_named_provider_in_dev_mode(monkeypatch):
    """In MOCK_PROVIDERS=true (offline dev), mock results are useful
    so the developer sees something on screen. The guard must ONLY
    fire in strict mode."""
    s = get_settings().model_copy(update={"mock_providers": True})
    monkeypatch.setattr(evidence_service, "get_settings", lambda: s)
    case_id = _fake_case_id()
    stored, _ = evidence_service.normalize_and_store_with_stats(
        case_id, [_mock_archive_result()]
    )
    assert any(e.provider == "mock_archive" for e in stored), stored


# ---------- end-to-end: real Wayback capture survives ----------

@pytest.mark.asyncio
@respx.mock
async def test_wayback_real_capture_normalized():
    """Confirms strict-mode hardening did not regress the real
    Wayback path — a successful 200 capture is still parsed into a
    real ARCHIVE ProviderResult."""
    cdx_payload = (
        # CDXJ-style header + one row
        "[\n"
        "  [\"urlkey\", \"timestamp\", \"original\", \"mimetype\", "
        "\"statuscode\", \"digest\", \"length\"],\n"
        "  [\"org,example)/news/1\", \"20240101120000\", "
        "\"https://example.org/news/1\", \"text/html\", \"200\", "
        "\"3I42H3S6NNFQ2MSVX7XZKYAYSCX5QBYJ\", \"4242\"]\n"
        "]"
    )
    respx.get(CDX_ENDPOINT).mock(
        return_value=httpx.Response(
            200,
            text=cdx_payload,
            headers={"content-type": "application/json"},
        )
    )
    out = await WaybackArchiveProvider().lookup(
        "https://example.org/news/1", limit=5
    )
    assert out, "expected at least one Wayback capture"
    e = out[0]
    assert e.provider == "wayback_cdx"
    assert e.source_type == SourceType.ARCHIVE
    assert "web.archive.org/web/" in (e.url or "")
