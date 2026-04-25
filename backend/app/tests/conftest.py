"""Pytest fixtures and test-suite-wide settings overrides.

The operator's local backend/.env may have real API keys and
MOCK_PROVIDERS=false for live dev. Tests must NOT inherit that —
they must run hermetically with mock providers and zero outbound
HTTP. Each test that needs to test the real-API path uses respx to
mock httpx explicitly.
"""
from __future__ import annotations

import os

# Force-mock mode + neutralize every provider credential BEFORE app imports.
# Pydantic BaseSettings prefers process env over .env file values, so SETTING
# the key to "" defeats whatever the operator put in their local .env.
# (POPping wouldn't help — .env would then take precedence.)
os.environ["MOCK_PROVIDERS"] = "true"
for _k in (
    "BRAVE_SEARCH_API_KEY", "GOOGLE_MAPS_API_KEY", "GOOGLE_KG_API_KEY",
    "GOOGLE_VISION_API_KEY", "GOOGLE_APPLICATION_CREDENTIALS",
    "YOUTUBE_API_KEY",
    "TELEGRAM_API_ID", "TELEGRAM_API_HASH", "TELEGRAM_BOT_TOKEN",
    "META_APP_ID", "META_APP_SECRET", "META_REDIRECT_URI",
    "REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET",
    "OPENAI_API_KEY", "GEOSEER_API_KEY", "PICARTA_API_KEY",
    "AZURE_VISION_ENDPOINT", "AZURE_VISION_KEY",
    "LOCATIONIQ_API_KEY",
    "GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_SECRET",
    "SERPAPI_API_KEY", "DATAFORSEO_LOGIN", "DATAFORSEO_PASSWORD",
):
    os.environ[_k] = ""

from collections.abc import Iterator  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.config import get_settings  # noqa: E402

# Reset the settings cache in case any earlier import populated it.
get_settings.cache_clear()

from app.main import create_app  # noqa: E402
from app.services.store import get_store  # noqa: E402


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
