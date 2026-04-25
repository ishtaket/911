"""Case CRUD."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.audit import AuditEntryCreate
from app.schemas.case import Case, CaseCreate
from app.schemas.person import Person
from app.services import audit_service
from app.services.store import get_store

router = APIRouter()


@router.post("", response_model=Case, status_code=201)
def create_case(payload: CaseCreate) -> Case:
    case = Case(
        title=payload.title,
        description=payload.description,
        person=Person(**payload.person.model_dump()),
        last_seen_at=payload.last_seen_at,
        last_seen_location=payload.last_seen_location,
        last_seen_lat=payload.last_seen_lat,
        last_seen_lon=payload.last_seen_lon,
        languages=payload.languages,
        risk_notes=payload.risk_notes,
        operator_id=payload.operator_id,
    )
    get_store().add_case(case)
    audit_service.log(
        AuditEntryCreate(
            actor_id=payload.operator_id,
            action="case.create",
            target_type="case",
            target_id=str(case.id),
        )
    )
    return case


@router.get("", response_model=list[Case])
def list_cases() -> list[Case]:
    return get_store().list_cases()


@router.get("/{case_id}", response_model=Case)
def get_case(case_id: UUID) -> Case:
    case = get_store().get_case(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="case not found")
    return case
