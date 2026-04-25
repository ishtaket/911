"""Search orchestration endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.evidence import Evidence
from app.schemas.query import QueryPlan
from app.services import search_orchestrator, search_service
from app.services.search_orchestrator import ArchiveStartResponse, WebSocialStartResponse
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


@router.post("/web/start/{case_id}", response_model=WebSocialStartResponse)
async def start_web_search(case_id: UUID) -> WebSocialStartResponse:
    """Web-search dispatch — returns a structured response with `state`,
    per-provider info, items_returned, and items_deduped so the operator
    UI can distinguish "no results" from "no providers configured" from
    "all results were duplicates of evidence already on the case"."""
    return await search_orchestrator.run_web(_require_case(case_id))


@router.post("/social/start/{case_id}", response_model=WebSocialStartResponse)
async def start_social_search(case_id: UUID) -> WebSocialStartResponse:
    """Social-search dispatch — same structured response shape as web."""
    return await search_orchestrator.run_social(_require_case(case_id))


@router.post("/archive/start/{case_id}", response_model=ArchiveStartResponse)
async def start_archive_search(case_id: UUID) -> ArchiveStartResponse:
    """Strict archive dispatch — only queries URL anchors that already
    exist in the case's evidence (P1/P2) plus host-wildcards derived from
    them (P3). Returns a structured response with a `state` field so the
    UI can distinguish 'no_targets' from 'no_results' from 'completed'."""
    return await search_orchestrator.run_archive(_require_case(case_id))


@router.get("/plan/{case_id}", response_model=QueryPlan)
def get_query_plan(case_id: UUID) -> QueryPlan:
    return search_service.case_query_plan(_require_case(case_id))
