"""Evidence schemas — normalized, deduplicated, validation-tracked."""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field

from app.schemas.provider_result import ProviderResult, SourceType
from app.schemas.validation import ValidationState


class EvidenceStatus(StrEnum):
    """Operator-facing label. Never use 'found'."""

    CANDIDATE = "candidate"
    NEEDS_REVIEW = "needs_review"
    CORROBORATED = "corroborated"
    REJECTED = "rejected"
    HUMAN_CONFIRMED = "human_confirmed"


class EvidenceCreate(BaseModel):
    case_id: UUID
    provider_result: ProviderResult
    related_person_id: UUID | None = None
    note: str | None = None


class Evidence(BaseModel):
    id: UUID = Field(default_factory=uuid4)
    case_id: UUID
    related_person_id: UUID | None = None
    source_type: SourceType
    provider: str
    title: str | None = None
    url: str | None = None
    snippet: str | None = None
    language: str | None = None
    confidence: float = 0.0
    risk_flags: list[str] = Field(default_factory=list)
    content_hash: str | None = None
    status: EvidenceStatus = EvidenceStatus.CANDIDATE
    validation: ValidationState = Field(default_factory=ValidationState)
    next_action: str | None = None
    note: str | None = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
