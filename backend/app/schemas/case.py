"""Case schemas — a missing-person case."""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field

from app.schemas.person import Person, PersonCreate


class CaseStatus(StrEnum):
    OPEN = "open"
    INVESTIGATING = "investigating"
    HUMAN_REVIEW = "human_review"
    RESOLVED = "resolved"
    CLOSED = "closed"


class CaseCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    description: str | None = None
    person: PersonCreate
    last_seen_at: datetime | None = None
    last_seen_location: str | None = None
    last_seen_lat: float | None = None
    last_seen_lon: float | None = None
    languages: list[str] = Field(default_factory=lambda: ["en", "he", "ru"])
    risk_notes: str | None = None
    operator_id: str | None = None  # who created it


class CaseUpdate(BaseModel):
    title: str | None = None
    description: str | None = None
    status: CaseStatus | None = None
    risk_notes: str | None = None


class Case(BaseModel):
    id: UUID = Field(default_factory=uuid4)
    title: str
    description: str | None = None
    status: CaseStatus = CaseStatus.OPEN
    person: Person
    last_seen_at: datetime | None = None
    last_seen_location: str | None = None
    last_seen_lat: float | None = None
    last_seen_lon: float | None = None
    languages: list[str] = Field(default_factory=lambda: ["en", "he", "ru"])
    risk_notes: str | None = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    operator_id: str | None = None
