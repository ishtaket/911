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
    valid_states = {"disabled", "not_configured", "auth_required", "connected", "rate_limited", "error", "mock"}
    for p in r.json()["providers"]:
        assert p["state"] in valid_states, p
        assert p["auth_type"] in {"none", "api_key", "oauth2", "manual_token", "service_account"}
        assert "safe_scope_description" in p and p["safe_scope_description"]


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


def test_per_channel_web_dispatch_returns_evidence_list():
    case_id = _seed_case_id()
    r = client.post(f"/v1/search/web/start/{case_id}")
    assert r.status_code == 200
    items = r.json()
    assert isinstance(items, list)
    # Mock providers always return at least one item per query variant.
    assert all("source_type" in it for it in items)


def test_per_channel_social_dispatch_returns_evidence_list():
    r = client.post(f"/v1/search/social/start/{_seed_case_id()}")
    assert r.status_code == 200
    assert isinstance(r.json(), list)


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
