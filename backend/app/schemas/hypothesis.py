"""Hypothesis schemas — ranked location/identity candidates."""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field

from app.schemas.validation import ValidationState


class RiskLevel(StrEnum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class HypothesisCreate(BaseModel):
    case_id: UUID
    hypothesis_type: str = "location"  # "location" | "identity" | "social_handle"
    label: str
    lat: float | None = None
    lon: float | None = None
    place_name: str | None = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    evidence_ids: list[UUID] = Field(default_factory=list)
    contradictions: list[str] = Field(default_factory=list)
    risk: RiskLevel = RiskLevel.MEDIUM
    next_checks: list[str] = Field(default_factory=list)


class Hypothesis(HypothesisCreate):
    id: UUID = Field(default_factory=uuid4)
    validation: ValidationState = Field(default_factory=ValidationState)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
