"""Tests for /health top-level alias, /v1/provider-status, and seeded cases."""
from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import create_app


def _client() -> TestClient:
    return TestClient(create_app())


def test_top_level_health_alias_works():
    with _client() as c:
        r = c.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert "languages" in body
    assert "mock_providers" in body


def test_provider_status_returns_categorized_list():
    with _client() as c:
        r = c.get("/v1/provider-status")
    assert r.status_code == 200
    body = r.json()
    assert isinstance(body["mock_providers"], bool)
    assert isinstance(body["providers"], list)
    assert len(body["providers"]) >= 20  # web + social + archive + geoint + maps

    categories = {p["category"] for p in body["providers"]}
    assert categories == {"web_search", "social", "archive", "geoint", "maps"}

    modes = {p["mode"] for p in body["providers"]}
    assert modes <= {"live", "mock", "ready", "stub"}

    # Public/keyless providers should always be READY, not MOCK.
    by_name = {p["name"]: p for p in body["providers"]}
    assert by_name["Wayback CDX"]["mode"] == "ready"
    assert by_name["OSM Nominatim"]["mode"] == "ready"
    assert by_name["EXIF reader"]["mode"] == "ready"


def test_seeded_cases_visible_after_startup():
    with _client() as c:
        r = c.get("/v1/cases")
    assert r.status_code == 200
    cases = r.json()
    assert len(cases) >= 3

    titles = {c["title"] for c in cases}
    assert {"Anna L.", "Yossi B.", "Dina K."} <= titles

    # Israel-themed locations seeded.
    locations = {c["last_seen_location"] for c in cases if c.get("last_seen_location")}
    assert "Tel Aviv-Yafo" in locations
    assert "Mount Meron trail" in locations
    assert "Haifa central bus station" in locations


def test_seeded_evidence_visible_for_first_case():
    with _client() as c:
        cases = c.get("/v1/cases").json()
        anna = next(x for x in cases if x["title"] == "Anna L.")
        ev = c.get("/v1/evidence", params={"case_id": anna["id"]}).json()
    assert len(ev) >= 1
    # Validation states must be present and shaped correctly.
    for e in ev:
        assert "validation" in e
        assert "level1" in e["validation"]
        assert "level2" in e["validation"]
        assert "level3" in e["validation"]


def test_seeded_hypotheses_visible_for_first_case():
    with _client() as c:
        cases = c.get("/v1/cases").json()
        anna = next(x for x in cases if x["title"] == "Anna L.")
        hyps = c.get("/v1/hypotheses", params={"case_id": anna["id"]}).json()
    assert len(hyps) >= 1
    h = hyps[0]
    assert h["place_name"] == "Tel Aviv-Yafo"
    assert h["lat"] is not None
    assert h["lon"] is not None
    assert 0.0 <= h["confidence"] <= 1.0
