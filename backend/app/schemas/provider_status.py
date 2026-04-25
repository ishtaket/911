"""Provider status DTOs.

Two API surfaces:
  - Legacy `ProviderStatusResponse` (mock/live/ready/stub) is kept for
    compatibility with `GET /v1/provider-status`.
  - New `ProviderListResponse` (full state machine) is served by
    `GET /v1/providers` and `POST /v1/providers/{id}/check`.
"""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field


# ---------- legacy (kept for compat) ----------

class ProviderMode(StrEnum):
    LIVE = "live"
    MOCK = "mock"
    READY = "ready"
    STUB = "stub"


class ProviderInfo(BaseModel):
    name: str
    category: str
    mode: ProviderMode
    note: str | None = None


class ProviderStatusResponse(BaseModel):
    mock_providers: bool
    providers: list[ProviderInfo] = Field(default_factory=list)


# ---------- new (richer) ----------

class ProviderState(StrEnum):
    DISABLED = "disabled"               # explicitly turned off
    NOT_CONFIGURED = "not_configured"   # missing api key / secret
    AUTH_REQUIRED = "auth_required"     # OAuth flow not completed
    CONNECTED = "connected"             # configured and reachable
    RATE_LIMITED = "rate_limited"
    ERROR = "error"
    MOCK = "mock"                        # safe deterministic stand-in


class ProviderAuthType(StrEnum):
    NONE = "none"
    API_KEY = "api_key"
    OAUTH2 = "oauth2"
    MANUAL_TOKEN = "manual_token"
    SERVICE_ACCOUNT = "service_account"


class ProviderType(StrEnum):
    WEB_SEARCH = "web_search"
    PUBLIC_SOCIAL = "public_social"
    ARCHIVE = "archive"
    GEOINT = "geoint"
    YOUTUBE = "youtube"
    TELEGRAM_PUBLIC = "telegram_public"
    META_PUBLIC_PAGES = "meta_public_pages"
    X_PUBLIC = "x_public"
    REDDIT_PUBLIC = "reddit_public"
    VK_PUBLIC = "vk_public"
    MAPS_GEOCODE = "maps_geocode"
    VISION_OCR = "vision_ocr"


class ProviderInfoV2(BaseModel):
    """One row in `GET /v1/providers`. No secrets. Safe to render in UI."""
    provider_id: str
    type: ProviderType
    display_name: str
    state: ProviderState
    auth_type: ProviderAuthType
    configured: bool
    requires_user_action: bool = False
    connect_url: str | None = None
    safe_scope_description: str
    last_checked_at: datetime | None = None
    last_error: str | None = None
    note: str | None = None


class ProviderListResponse(BaseModel):
    mock_providers: bool
    providers: list[ProviderInfoV2] = Field(default_factory=list)


class ProviderCheckResponse(BaseModel):
    """`POST /v1/providers/{id}/check` result."""
    provider: ProviderInfoV2
    ok: bool
    message: str
