"""Application settings, loaded from environment / .env."""
from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Backend settings. All secrets come from environment, never repo."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_env: Literal["development", "staging", "production"] = "development"
    app_region: str = "IL"
    app_languages: str = "en,he,ru"

    database_url: str = "postgresql+psycopg://osint:osint@localhost:5432/osint"
    redis_url: str = "redis://localhost:6379/0"
    opensearch_url: str | None = None
    qdrant_url: str | None = None
    s3_endpoint_url: str | None = None
    s3_bucket: str = "osint-cases"
    s3_access_key: str | None = None
    s3_secret_key: str | None = None

    brave_search_api_key: str | None = None
    google_maps_api_key: str | None = None
    google_application_credentials: str | None = None
    locationiq_api_key: str | None = None

    openai_api_key: str | None = None
    geoseer_api_key: str | None = None
    picarta_api_key: str | None = None
    azure_vision_endpoint: str | None = None
    azure_vision_key: str | None = None

    youtube_api_key: str | None = None
    google_vision_api_key: str | None = None
    google_kg_api_key: str | None = None
    # Google Custom Search JSON API (Programmable Search). Both must be set
    # for the provider to be "connected". Do NOT reuse google_maps_api_key
    # for CSE — CSE billing/quota is tracked separately by key.
    google_cse_api_key: str | None = None
    google_cse_engine_id: str | None = None

    # Custom Search Site Restricted JSON API. Per Google's official docs,
    # this endpoint was retired on 2025-01-08. We keep the provider in
    # the registry for visibility / migration messaging, gated by an
    # explicit enable flag so it never silently calls a dead endpoint.
    google_cse_site_restricted_enabled: bool = False

    # Vertex AI Search (a.k.a. Discovery Engine / "Agent Search").
    # Official Google migration path for the retired Site Restricted
    # JSON API. The `searchLite` method is API-key-authenticated and
    # is restricted to public-website data stores — exactly the
    # workload the Site Restricted JSON API used to handle.
    #
    # Required for `connected`:
    #   - vertex_ai_search_enabled = True
    #   - vertex_ai_project_id, vertex_ai_engine_id all set
    #   - vertex_ai_api_key set (defaults to reusing google_cse_api_key
    #     if a separate key is not provided, since searchLite + the
    #     existing key share a Google Cloud project)
    vertex_ai_search_enabled: bool = False
    vertex_ai_project_id: str | None = None
    vertex_ai_location: str = "global"
    vertex_ai_engine_id: str | None = None
    vertex_ai_serving_config: str = "default_search"
    vertex_ai_api_key: str | None = None
    vertex_ai_collection: str = "default_collection"
    telegram_api_id: str | None = None
    telegram_api_hash: str | None = None
    meta_app_id: str | None = None
    meta_app_secret: str | None = None
    reddit_client_id: str | None = None
    reddit_client_secret: str | None = None

    mock_providers: bool = Field(default=True, description="Use mock providers when keys are missing.")

    @property
    def supported_languages(self) -> list[str]:
        return [s.strip() for s in self.app_languages.split(",") if s.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
