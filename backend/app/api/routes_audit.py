"""Audit log (RBAC TODO)."""
from __future__ import annotations

from fastapi import APIRouter, Query

from app.schemas.audit import AuditEntry
from app.services import audit_service

router = APIRouter()


@router.get("", response_model=list[AuditEntry])
def list_audit(limit: int = Query(default=200, ge=1, le=1000)) -> list[AuditEntry]:
    return audit_service.list_recent(limit=limit)
