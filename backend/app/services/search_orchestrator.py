"""Per-channel search orchestrator.

Wraps `search_service` (which today fans out to all channels) so callers
can dispatch only one channel at a time. The plumbing reaches the same
provider registry, evidence normalizer, and audit logger.
"""
from __future__ import annotations

from app.providers.registry import (
    get_archive_providers,
    get_social_search_providers,
    get_web_search_providers,
)
from app.schemas.audit import AuditEntryCreate
from app.schemas.case import Case
from app.schemas.evidence import Evidence
from app.services import audit_service, evidence_service
from app.services.query_builder import build_query_plan
from app.services.search_service import _search_archive, _search_social, _search_web


async def run_web(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    raw = await _search_web(plan, get_web_search_providers())
    return evidence_service.normalize_and_store(case.id, raw)


async def run_social(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    raw = await _search_social(plan, get_social_search_providers())
    return evidence_service.normalize_and_store(case.id, raw)


async def run_archive(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    raw = await _search_archive(plan, get_archive_providers())
    stored = evidence_service.normalize_and_store(case.id, raw)
    if not stored:
        # Record the empty-result event for audit/observability — distinct
        # from per-provider errors (which the orchestrator already logs).
        audit_service.log(
            AuditEntryCreate(
                action="archive_no_results",
                target_type="archive",
                target_id=str(case.id),
                metadata={"providers_attempted": len(get_archive_providers())},
            )
        )
    return stored


__all__ = ["run_web", "run_social", "run_archive"]
