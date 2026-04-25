"""Search orchestration endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.evidence import Evidence
from app.schemas.query import QueryPlan
from app.services import search_service
from app.services.store import get_store

router = APIRouter()


@router.post("/start/{case_id}", response_model=list[Evidence])
async def start_search(case_id: UUID) -> list[Evidence]:
    case = get_store().get_case(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="case not found")
    return await search_service.run_search_for_case(case)


@router.get("/plan/{case_id}", response_model=QueryPlan)
def get_query_plan(case_id: UUID) -> QueryPlan:
    case = get_store().get_case(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="case not found")
    return search_service.case_query_plan(case)
