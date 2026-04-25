"""Audit log schemas — every external provider call and L3 review action."""
from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


class AuditEntryCreate(BaseModel):
    actor_id: str | None = None  # operator / system
    action: str  # e.g. "provider_call", "review.confirm", "case.create"
    target_type: str | None = None  # "case" | "evidence" | "provider" | ...
    target_id: str | None = None
    metadata: dict = Field(default_factory=dict)
    ip_address: str | None = None
    user_agent: str | None = None


class AuditEntry(AuditEntryCreate):
    id: UUID = Field(default_factory=uuid4)
    created_at: datetime = Field(default_factory=datetime.utcnow)
