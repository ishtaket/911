"""Hypothesis listing per case."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter

from app.schemas.hypothesis import Hypothesis
from app.services.store import get_store

router = APIRouter()


@router.get("", response_model=list[Hypothesis])
def list_hypotheses(case_id: UUID | None = None) -> list[Hypothesis]:
    return get_store().list_hypotheses(case_id=case_id)
