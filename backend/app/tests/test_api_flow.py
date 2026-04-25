"""End-to-end mocked flow: create case → search → review → audit."""
from __future__ import annotations

from fastapi.testclient import TestClient


def _create_case(client: TestClient, name: str = "Иван Петров") -> dict:
    payload = {
        "title": "Test missing case",
        "person": {"full_name": name, "name_variants": [name]},
        "last_seen_location": "Tel Aviv",
        "languages": ["en", "he", "ru"],
    }
    r = client.post("/v1/cases", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


def test_full_mocked_flow(client: TestClient) -> None:
    case = _create_case(client)
    case_id = case["id"]

    # query plan exists
    plan = client.get(f"/v1/search/plan/{case_id}").json()
    assert plan["case_id"] == case_id
    assert plan["variants"]

    # search runs and returns evidence
    r = client.post(f"/v1/search/start/{case_id}")
    assert r.status_code == 200
    evidence = r.json()
    assert evidence, "expected mock providers to return evidence"
    ev_id = evidence[0]["id"]

    # geoint analyze + promote
    r = client.post(
        "/v1/geoint/analyze",
        json={"case_id": case_id, "media_id": case_id},  # reuse uuid as fake media id
    )
    assert r.status_code == 200
    geo = r.json()
    job_id = geo["id"]
    r = client.post(f"/v1/geoint/promote/{job_id}")
    assert r.status_code == 200
    hypotheses = r.json()
    assert hypotheses

    # L3 review: confirm
    r = client.post(
        f"/v1/review/{ev_id}",
        json={"action": "confirm", "reviewer_id": "ops:test", "note": "ok"},
    )
    assert r.status_code == 200
    confirmed = r.json()
    assert confirmed["status"] == "human_confirmed"
    assert confirmed["validation"]["level3"]["action"] == "confirm"

    # audit log captures provider calls + review
    r = client.get("/v1/audit?limit=500")
    assert r.status_code == 200
    actions = {entry["action"] for entry in r.json()}
    assert "case.create" in actions
    assert "review.confirm" in actions
    assert "provider_call" in actions
