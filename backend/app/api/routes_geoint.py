"""GeoINT analysis endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult
from app.schemas.hypothesis import Hypothesis
from app.services import geoint_service, hypothesis_service
from app.services.store import get_store

router = APIRouter()


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
