"""Health and readiness endpoints."""
from __future__ import annotations

from fastapi import APIRouter

from app.config import get_settings

router = APIRouter()


@router.get("/health")
def health() -> dict:
    settings = get_settings()
    return {
        "status": "ok",
        "env": settings.app_env,
        "region": settings.app_region,
        "languages": settings.supported_languages,
        "mock_providers": settings.mock_providers,
    }


@router.get("/health/ready")
def ready() -> dict:
    return {"ready": True}
