"""Audit service — append-only log of every external provider call and L3 review."""
from __future__ import annotations

from app.schemas.audit import AuditEntry, AuditEntryCreate
from app.services.store import get_store


def log(entry: AuditEntryCreate) -> AuditEntry:
    record = AuditEntry(**entry.model_dump())
    return get_store().add_audit(record)


def list_recent(limit: int = 200) -> list[AuditEntry]:
    return get_store().list_audit(limit=limit)
