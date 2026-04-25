"""Evidence list/detail endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.evidence import Evidence
from app.services.store import get_store

router = APIRouter()


@router.get("", response_model=list[Evidence])
def list_evidence(case_id: UUID | None = None) -> list[Evidence]:
    return get_store().list_evidence(case_id=case_id)


@router.get("/{evidence_id}", response_model=Evidence)
def get_evidence(evidence_id: UUID) -> Evidence:
    e = get_store().get_evidence(evidence_id)
    if e is None:
        raise HTTPException(status_code=404, detail="evidence not found")
    return e
