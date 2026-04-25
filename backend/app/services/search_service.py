"""Search orchestrator — runs query plan across web/social/archive providers."""
from __future__ import annotations

import asyncio
from uuid import UUID

from app.providers.base import ArchiveProvider, SocialSearchProvider, WebSearchProvider
from app.providers.registry import (
    get_archive_providers,
    get_social_search_providers,
    get_web_search_providers,
)
from app.schemas.audit import AuditEntryCreate
from app.schemas.case import Case
from app.schemas.evidence import Evidence
from app.schemas.provider_result import ProviderResult
from app.schemas.query import QueryChannel, QueryPlan
from app.services import audit_service, evidence_service
from app.services.query_builder import build_query_plan


async def _search_web(plan: QueryPlan, providers: list[WebSearchProvider]) -> list[ProviderResult]:
    out: list[ProviderResult] = []
    for v in plan.variants:
        if v.channel != QueryChannel.WEB:
            continue
        for p in providers:
            audit_service.log(
                AuditEntryCreate(
                    action="provider_call",
                    target_type="web_search",
                    metadata={"provider": p.name, "query": v.text, "lang": v.language},
                )
            )
            try:
                results = await p.search(v.text, language=v.language, limit=5)
                out.extend(results)
            except Exception as exc:  # noqa: BLE001
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type="web_search",
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )
    return out


async def _search_social(
    plan: QueryPlan, providers: list[SocialSearchProvider]
) -> list[ProviderResult]:
    out: list[ProviderResult] = []
    for v in plan.variants:
        if v.channel != QueryChannel.SOCIAL:
            continue
        for p in providers:
            audit_service.log(
                AuditEntryCreate(
                    action="provider_call",
                    target_type="social_search",
                    metadata={"provider": p.name, "query": v.text, "lang": v.language},
                )
            )
            try:
                results = await p.search(v.text, language=v.language, limit=5)
                out.extend(results)
            except Exception as exc:  # noqa: BLE001
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type="social_search",
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )
    return out


async def _search_archive(
    plan: QueryPlan, providers: list[ArchiveProvider]
) -> list[ProviderResult]:
    out: list[ProviderResult] = []
    for v in plan.variants:
        if v.channel != QueryChannel.ARCHIVE:
            continue
        for p in providers:
            audit_service.log(
                AuditEntryCreate(
                    action="provider_call",
                    target_type="archive",
                    metadata={"provider": p.name, "query": v.text},
                )
            )
            try:
                results = await p.lookup(v.text, limit=5)
                out.extend(results)
            except Exception as exc:  # noqa: BLE001
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type="archive",
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )
    return out


async def run_search_for_case(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    web_results, social_results, archive_results = await asyncio.gather(
        _search_web(plan, get_web_search_providers()),
        _search_social(plan, get_social_search_providers()),
        _search_archive(plan, get_archive_providers()),
    )
    all_results = [*web_results, *social_results, *archive_results]
    return evidence_service.normalize_and_store(case.id, all_results)


def case_query_plan(case: Case) -> QueryPlan:
    return build_query_plan(case)


__all__ = ["run_search_for_case", "case_query_plan", "UUID"]
