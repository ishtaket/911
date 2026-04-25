"""Smoke test — health endpoints respond."""
from __future__ import annotations

from fastapi.testclient import TestClient


def test_root(client: TestClient) -> None:
    r = client.get("/")
    assert r.status_code == 200
    body = r.json()
    assert body["name"] == "Rescue911 OSINT Backend"


def test_health(client: TestClient) -> None:
    r = client.get("/v1/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["region"] == "IL"
    assert "en" in body["languages"]
    assert "he" in body["languages"]
    assert "ru" in body["languages"]


def test_ready(client: TestClient) -> None:
    r = client.get("/v1/health/ready")
    assert r.status_code == 200
    assert r.json() == {"ready": True}
