"""GeoINT analysis endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult
from app.schemas.hypothesis import Hypothesis
from app.services import geoint_service, hypothesis_service
from app.services.store import get_store

router = APIRouter()


class GeoIntStartResponse(BaseModel):
    """Per-case GeoINT dispatch result. The GeoINT pipeline only runs on
    media uploaded for the case — if there is none, we return a structured
    `no_media_uploaded` state instead of failing or fabricating evidence."""
    state: str  # "no_media_uploaded" | "queued" | "completed"
    case_id: UUID
    message: str
    results: list[GeoIntResult] = []


@router.post("/start/{case_id}", response_model=GeoIntStartResponse)
def start_geoint(case_id: UUID) -> GeoIntStartResponse:
    case = get_store().get_case(case_id)
    if case is None:
        raise HTTPException(status_code=404, detail="case not found")
    # Media-upload pipeline (POST /v1/media) is not wired yet. Until it is,
    # GeoINT cannot have anything to analyze for this case. Return a clear
    # state so the UI can show 'upload media first', not a fake success.
    return GeoIntStartResponse(
        state="no_media_uploaded",
        case_id=case_id,
        message="GeoINT requires uploaded media. Use POST /v1/media (not yet wired) "
                "to upload an image, then re-dispatch.",
        results=[],
    )


@router.post("/analyze", response_model=GeoIntResult)
async def analyze(request: GeoIntAnalyzeRequest) -> GeoIntResult:
    result = await geoint_service.analyze(request)
    return result


@router.get("/{job_id}", response_model=GeoIntResult)
def get_result(job_id: UUID) -> GeoIntResult:
    result = get_store().get_geoint_result(job_id)
    if result is None:
        raise HTTPException(status_code=404, detail="geoint job not found")
    return result


@router.post("/promote/{job_id}", response_model=list[Hypothesis])
def promote_to_hypotheses(job_id: UUID) -> list[Hypothesis]:
    result = get_store().get_geoint_result(job_id)
    if result is None:
        raise HTTPException(status_code=404, detail="geoint job not found")
    return hypothesis_service.hypotheses_from_geoint(result.case_id, result)
