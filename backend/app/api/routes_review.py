"""Level-3 human review actions."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.schemas.evidence import Evidence
from app.schemas.validation import ValidationLevel3Action
from app.services import validation_service

router = APIRouter()


class ReviewBody(BaseModel):
    action: ValidationLevel3Action
    reviewer_id: str | None = None
    note: str | None = None


@router.post("/{evidence_id}", response_model=Evidence)
def review(evidence_id: UUID, body: ReviewBody) -> Evidence:
    e = validation_service.review_evidence(
        evidence_id=evidence_id,
        action=body.action,
        reviewer_id=body.reviewer_id,
        note=body.note,
    )
    if e is None:
        raise HTTPException(status_code=404, detail="evidence not found")
    return e
