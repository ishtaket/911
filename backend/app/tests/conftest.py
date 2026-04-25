"""Pytest fixtures."""
from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.services.store import get_store


@pytest.fixture()
def client() -> Iterator[TestClient]:
    # reset in-memory store between tests
    store = get_store()
    store.cases.clear()
    store.evidence.clear()
    store.hypotheses.clear()
    store.geoint_results.clear()
    store.audit.clear()
    app = create_app()
    with TestClient(app) as c:
        yield c
