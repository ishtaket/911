"""Provider status DTO — what the Android Settings/Providers screen reads."""
from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, Field


class ProviderMode(StrEnum):
    LIVE = "live"             # real provider, key configured
    MOCK = "mock"             # mock fallback, no key required
    READY = "ready"           # public, no key required, real implementation
    STUB = "stub"             # interface exists but no real implementation yet


class ProviderInfo(BaseModel):
    name: str
    category: str            # "web_search" | "social" | "archive" | "geoint" | "maps"
    mode: ProviderMode
    note: str | None = None


class ProviderStatusResponse(BaseModel):
    mock_providers: bool
    providers: list[ProviderInfo] = Field(default_factory=list)
