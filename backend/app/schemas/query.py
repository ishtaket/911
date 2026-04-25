"""Query schemas — multilingual, Israel-aware query plans."""
from __future__ import annotations

from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


class QueryChannel(StrEnum):
    WEB = "web"
    SOCIAL = "social"
    ARCHIVE = "archive"
    MAPS = "maps"


class QueryVariant(BaseModel):
    text: str
    language: str  # "en" | "he" | "ru" | "ar"
    channel: QueryChannel
    weight: float = 1.0  # ranking weight for this variant


class QueryPlan(BaseModel):
    """A set of query variants generated for a case across channels and languages."""

    id: UUID = Field(default_factory=uuid4)
    case_id: UUID
    variants: list[QueryVariant]
    region: str = "IL"
    notes: str | None = None
