"""FastAPI entrypoint for the Rescue911 backend."""
from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import (
    routes_audit,
    routes_cases,
    routes_evidence,
    routes_geoint,
    routes_health,
    routes_hypotheses,
    routes_media,
    routes_provider_status,
    routes_review,
    routes_search,
)
from app.config import get_settings
from app.services import seed


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Rescue911 OSINT Backend",
        version="0.1.0",
        description=(
            "Israel-focused OSINT + GeoINT missing-person backend. "
            "Public-data-only. Three-level validation. Audit-logged."
        ),
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],  # tightened per-env via reverse proxy in staging/prod
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(routes_health.router, prefix="/v1", tags=["health"])
    # Top-level /health alias for plain `curl http://host:8000/health`.
    app.include_router(routes_health.router, prefix="", tags=["health"])
    app.include_router(routes_cases.router, prefix="/v1/cases", tags=["cases"])
    app.include_router(routes_media.router, prefix="/v1/media", tags=["media"])
    app.include_router(routes_search.router, prefix="/v1/search", tags=["search"])
    app.include_router(routes_geoint.router, prefix="/v1/geoint", tags=["geoint"])
    app.include_router(routes_evidence.router, prefix="/v1/evidence", tags=["evidence"])
    app.include_router(routes_hypotheses.router, prefix="/v1/hypotheses", tags=["hypotheses"])
    app.include_router(routes_review.router, prefix="/v1/review", tags=["review"])
    app.include_router(routes_audit.router, prefix="/v1/audit", tags=["audit"])
    app.include_router(routes_provider_status.router, prefix="/v1/provider-status", tags=["providers"])

    @app.on_event("startup")
    def _seed() -> None:
        seed.seed_if_empty()

    @app.get("/", tags=["root"])
    def root() -> dict:
        return {
            "name": "Rescue911 OSINT Backend",
            "version": "0.1.0",
            "env": settings.app_env,
            "region": settings.app_region,
            "languages": settings.supported_languages,
            "mock_providers": settings.mock_providers,
        }

    return app


app = create_app()
