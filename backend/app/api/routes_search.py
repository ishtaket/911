"""Search orchestration endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.evidence import Evidence
from app.schemas.query import QueryPlan
from app.services import search_orchestrator, search_service
from app.services.store import get_store

router = APIRouter()


def _require_case(case_id: UUID):
    case = get_store().get_case(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="case not found")
    return case


@router.post("/start/{case_id}", response_model=list[Evidence])
async def start_search(case_id: UUID) -> list[Evidence]:
    """Compatibility: dispatches all channels (web + social + archive)."""
    return await search_service.run_search_for_case(_require_case(case_id))


@router.post("/web/start/{case_id}", response_model=list[Evidence])
async def start_web_search(case_id: UUID) -> list[Evidence]:
    return await search_orchestrator.run_web(_require_case(case_id))


@router.post("/social/start/{case_id}", response_model=list[Evidence])
async def start_social_search(case_id: UUID) -> list[Evidence]:
    return await search_orchestrator.run_social(_require_case(case_id))


@router.post("/archive/start/{case_id}", response_model=list[Evidence])
async def start_archive_search(case_id: UUID) -> list[Evidence]:
    return await search_orchestrator.run_archive(_require_case(case_id))


@router.get("/plan/{case_id}", response_model=QueryPlan)
def get_query_plan(case_id: UUID) -> QueryPlan:
    return search_service.case_query_plan(_require_case(case_id))
