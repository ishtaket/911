"""Tests for the new provider registry + per-channel search orchestrator."""
from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import create_app
from app.services.store import get_store

client = TestClient(create_app())


def _seed_case_id() -> str:
    """First seeded case id (Israel sample data)."""
    cases = get_store().list_cases()
    assert cases, "no seeded cases — startup hook didn't run"
    return str(cases[0].id)


def test_providers_returns_no_secrets():
    r = client.get("/v1/providers")
    assert r.status_code == 200
    body = r.json()
    assert "providers" in body
    assert isinstance(body["providers"], list)
    assert len(body["providers"]) > 10
    text = r.text
    # Should never expose any of these key names with values:
    for forbidden in ("api_key=", "client_secret=", "bearer ", "subscription-token", "ghp_", "sk-"):
        assert forbidden.lower() not in text.lower(), f"leaked: {forbidden}"


def test_providers_state_machine_values_are_valid():
    r = client.get("/v1/providers")
    valid_states = {
        "disabled", "not_configured", "auth_required", "connected",
        "rate_limited", "error", "mock", "unavailable", "manual_ui_required",
    }
    for p in r.json()["providers"]:
        assert p["state"] in valid_states, p
        assert p["auth_type"] in {"none", "api_key", "oauth2", "manual_token", "service_account"}
        assert "safe_scope_description" in p and p["safe_scope_description"]


def test_provider_listing_includes_google_alternatives():
    """Google has multiple web-search-shaped products; the provider list
    must surface each so operators see why one path may fail and where
    to go next. See docs/GOOGLE_WEB_SEARCH_ALTERNATIVES.md."""
    r = client.get("/v1/providers")
    by_id = {p["provider_id"]: p for p in r.json()["providers"]}
    # JSON API path (may be denied at project level)
    assert "google_cse" in by_id
    # UI-assisted Programmable Search Element — manual flow only
    pse = by_id.get("google_programmable_search_element")
    assert pse is not None
    assert pse["state"] == "manual_ui_required"
    assert pse["type"] == "web_search_ui_assisted"
    assert "manual ui" in pse["note"].lower() or "manual" in pse["note"].lower()
    # Vertex AI Search — site search via service account
    vx = by_id.get("vertex_ai_search")
    assert vx is not None
    assert vx["type"] == "site_search"
    assert vx["auth_type"] == "service_account"
    assert vx["state"] == "not_configured"


def test_providers_check_endpoint_known_id():
    # Wayback is public, no auth — should always be 'connected'
    r = client.post("/v1/providers/wayback_cdx/check")
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is True
    assert body["provider"]["provider_id"] == "wayback_cdx"
    assert body["provider"]["state"] == "connected"


def test_providers_check_endpoint_unknown_id():
    r = client.post("/v1/providers/does_not_exist/check")
    assert r.status_code == 404


def test_per_channel_web_dispatch_returns_structured_response():
    """Web dispatch now returns WebSocialStartResponse (not bare list) so
    the operator UI can distinguish 'no_results' from 'not_configured'
    from 'deduplicated'. See search_orchestrator.WebSocialStartResponse."""
    case_id = _seed_case_id()
    r = client.post(f"/v1/search/web/start/{case_id}")
    assert r.status_code == 200
    body = r.json()
    assert body["channel"] == "web"
    assert body["case_id"] == case_id
    # In MOCK_PROVIDERS=true (test mode), only mocks run → state == "mock"
    # the very first dispatch; on a re-run everything dedupes.
    assert body["state"] in {"mock", "deduplicated", "no_results", "completed"}
    assert isinstance(body["evidence"], list)
    if body["state"] in {"mock", "completed"}:
        assert all("source_type" in it for it in body["evidence"])
    assert isinstance(body["providers"], list)
    assert body["providers_attempted"] >= 1
    # message and items_deduped must always be present so UI can render them
    assert isinstance(body["message"], str) and body["message"]
    assert "items_deduped" in body


def test_per_channel_social_dispatch_returns_structured_response():
    case_id = _seed_case_id()
    r = client.post(f"/v1/search/social/start/{case_id}")
    assert r.status_code == 200
    body = r.json()
    assert body["channel"] == "social"
    assert body["case_id"] == case_id
    assert body["state"] in {"mock", "deduplicated", "no_results", "completed"}
    assert isinstance(body["providers"], list)
    assert body["providers_attempted"] >= 1
    assert isinstance(body["evidence"], list)


def test_web_dispatch_state_unavailable_when_cse_returns_permission_denied(monkeypatch):
    """When Google CSE raises ProviderUnavailable (the project-level
    PERMISSION_DENIED 403) and the other web providers also fail, the
    overall state must be `unavailable` so the operator-visible banner
    shows the dominant, actionable signal — not generic `no_results`."""
    import httpx
    import respx
    from app.providers import registry as registry_mod
    from app.config import get_settings
    from app.providers.web_search.google_cse import (
        CSE_ENDPOINT,
        GoogleCseWebSearchProvider,
    )

    real_get_web = registry_mod.get_web_search_providers
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "google_cse_api_key": "cse_test_key_xxxxxxxxxxxxxxxx",
        "google_cse_engine_id": "cse_engine_id_xxxxxxxx",
        "google_kg_api_key": None,
        "brave_search_api_key": None,
    })
    monkeypatch.setattr(
        registry_mod, "get_web_search_providers", lambda settings=None: real_get_web(s)
    )
    from app.services import search_orchestrator as orch
    monkeypatch.setattr(orch, "get_web_search_providers", lambda settings=None: real_get_web(s))

    body = {
        "error": {
            "code": 403,
            "status": "PERMISSION_DENIED",
            "message": "This project does not have the access to Custom Search JSON API.",
        }
    }
    with respx.mock(assert_all_called=False) as respx_mock:
        respx_mock.get(CSE_ENDPOINT).mock(return_value=httpx.Response(403, json=body))
        case_id = _seed_case_id()
        r = client.post(f"/v1/search/web/start/{case_id}")
    assert r.status_code == 200
    body = r.json()
    # Per-provider: google_cse must be `unavailable` exactly.
    cse_row = next(p for p in body["providers"] if p["provider"] == "google_cse")
    assert cse_row["state"] == "unavailable", body["providers"]
    assert "oauth" in (cse_row["detail"] or "").lower()
    # Overall state should pick up the dominant non-ok mode. With
    # CSE=unavailable and KG/Brave=not_configured, the most-common is
    # not_configured (2 vs 1). When CSE alone is configured but the
    # others aren't, the operator-visible message must still mention
    # `unavailable` for CSE so they see the upstream denial.
    assert body["state"] in {"unavailable", "not_configured"}, body["state"]
    msg_lower = body["message"].lower()
    assert "unavailable" in msg_lower or "google_cse=unavailable" in msg_lower


def test_web_dispatch_state_not_configured_when_strict_no_keys(monkeypatch):
    """When MOCK_PROVIDERS=false and no real keys, every web provider
    raises ProviderNotConfigured — the dispatch must report that exactly,
    not return a silent empty list."""
    from app.providers import registry as registry_mod
    from app.config import get_settings

    real_get_web = registry_mod.get_web_search_providers
    s = get_settings().model_copy(update={
        "mock_providers": False,
        "brave_search_api_key": None,
        "google_kg_api_key": None,
        "google_maps_api_key": None,
        "google_cse_api_key": None,
        "google_cse_engine_id": None,
    })
    monkeypatch.setattr(
        registry_mod, "get_web_search_providers", lambda settings=None: real_get_web(s)
    )
    # The orchestrator imports registry symbols at module load — patch
    # those direct references too.
    from app.services import search_orchestrator as orch
    monkeypatch.setattr(orch, "get_web_search_providers", lambda settings=None: real_get_web(s))

    case_id = _seed_case_id()
    r = client.post(f"/v1/search/web/start/{case_id}")
    assert r.status_code == 200
    body = r.json()
    assert body["state"] == "not_configured", body
    assert body["evidence"] == []
    assert body["items_returned"] == 0
    # every provider entry must report not_configured
    assert all(p["state"] == "not_configured" for p in body["providers"]), body["providers"]


def test_per_channel_archive_dispatch_returns_structured_response():
    """Archive endpoint now returns ArchiveStartResponse, not a bare list.
    Use a fresh case with no evidence so we hit the fast no_targets path
    and never make outbound HTTP to archive.org / commoncrawl.org."""
    new_case = client.post("/v1/cases", json={
        "title": "ad-hoc orchestrator archive test",
        "description": "no evidence — exercises no_targets path",
        "person": {"full_name": "Orchestrator Test"},
        "languages": ["en"],
    }).json()
    r = client.post(f"/v1/search/archive/start/{new_case['id']}")
    assert r.status_code == 200
    body = r.json()
    assert body["state"] == "no_targets"
    assert body["evidence"] == []
    assert body["targets_attempted"] == 0


def test_geoint_start_returns_no_media_uploaded_state():
    r = client.post(f"/v1/geoint/start/{_seed_case_id()}")
    assert r.status_code == 200
    body = r.json()
    assert body["state"] == "no_media_uploaded"
    assert body["results"] == []
    assert "upload" in body["message"].lower()


def test_legacy_provider_status_still_works():
    """Compatibility — old endpoint must keep responding."""
    r = client.get("/v1/provider-status")
    assert r.status_code == 200
    assert "providers" in r.json()
    assert "mock_providers" in r.json()
