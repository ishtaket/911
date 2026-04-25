"""Normalized provider result — every external API normalizes into this shape."""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


class SourceType(StrEnum):
    WEB = "web"
    SOCIAL = "social"
    ARCHIVE = "archive"
    GEOINT = "geoint"
    MAPS = "maps"
    OCR = "ocr"
    VISION = "vision"
    EXIF = "exif"
    OFFICIAL = "official"


class ProviderResult(BaseModel):
    """One normalized item returned by a provider."""

    id: UUID = Field(default_factory=uuid4)
    provider: str  # e.g. "brave", "wayback", "picarta"
    source_type: SourceType
    title: str | None = None
    url: str | None = None
    snippet: str | None = None
    language: str | None = None
    captured_at: datetime | None = None  # when the source was captured (e.g. archive snapshot)
    fetched_at: datetime = Field(default_factory=datetime.utcnow)
    raw: dict = Field(default_factory=dict)  # preserved for audit / re-derivation
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    risk_flags: list[str] = Field(default_factory=list)
    is_legal_source: bool = True
    content_hash: str | None = None
