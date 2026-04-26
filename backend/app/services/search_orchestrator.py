"""Per-channel search orchestrator.

Wraps `search_service` (which today fans out to all channels) so callers
can dispatch only one channel at a time. The plumbing reaches the same
provider registry, evidence normalizer, and audit logger.
"""
from __future__ import annotations

import asyncio

from pydantic import BaseModel

from app.providers.base import (
    ProviderAuthRequired,
    ProviderNotConfigured,
    ProviderUnavailable,
    RateLimitError,
    SocialSearchProvider,
    WebSearchProvider,
)
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
from app.services.archive_target_builder import build_targets
from app.services.query_builder import build_query_plan
from app.services.store import get_store


class ProviderRunInfo(BaseModel):
    """Per-provider outcome of a single dispatch."""
    provider: str
    # One of: "ok", "not_configured", "auth_required", "unavailable",
    # "rate_limited", "error". `unavailable` means the upstream API has
    # denied this account/project at the org level — local config can't
    # fix it (distinct from "not_configured" which IS operator-fixable).
    state: str
    items: int = 0
    detail: str | None = None  # error message if state != ok


class WebSocialStartResponse(BaseModel):
    """Per-case dispatch result for web or social search.

    `state` is the most-informative summary the operator needs to act on:
      - "completed"        : ≥1 provider returned items AND ≥1 stored
      - "deduplicated"     : items returned but ALL filtered as duplicates
                             (tells the operator the case already has them)
      - "mock"             : only mock providers ran (MOCK_PROVIDERS=true)
      - "no_results"       : providers ran without errors but returned 0
      - "not_configured"   : every attempted provider raised
                             ProviderNotConfigured (no real keys)
      - "auth_required"    : every attempted provider needs OAuth
      - "rate_limited"     : every attempted provider was 429'd
      - "provider_error"   : ≥1 provider errored AND 0 stored items
    `providers` lists per-provider state so the UI can show *why*.
    """
    state: str
    case_id: str
    channel: str  # "web" | "social"
    providers_attempted: int
    providers_with_items: int
    items_returned: int
    items_deduped: int
    message: str
    providers: list[ProviderRunInfo] = []
    evidence: list[Evidence] = []


class ArchiveStartResponse(BaseModel):
    """Per-case archive dispatch result.

    `state` is one of:
      - "no_targets"  : case has no URLs yet — operator should run web/social
      - "no_results"  : targets attempted but providers returned nothing
      - "completed"   : at least one stored evidence item
    """
    state: str
    case_id: str
    targets_attempted: int
    message: str
    evidence: list[Evidence] = []


async def _run_channel(
    channel: str,
    plan: QueryPlan,
    providers: list[WebSearchProvider] | list[SocialSearchProvider],
    target_type_audit: str,
) -> tuple[list[ProviderResult], list[ProviderRunInfo]]:
    """Run all variants matching `channel` against `providers`, capturing
    per-provider state independently. One bad provider never poisons
    another — each error is audit-logged and surfaced in ProviderRunInfo."""
    expected = QueryChannel.WEB if channel == "web" else QueryChannel.SOCIAL
    raw: list[ProviderResult] = []
    info: dict[str, ProviderRunInfo] = {
        p.name: ProviderRunInfo(provider=p.name, state="ok", items=0)
        for p in providers
    }
    for v in plan.variants:
        if v.channel != expected:
            continue
        for p in providers:
            audit_service.log(
                AuditEntryCreate(
                    action="provider_call",
                    target_type=target_type_audit,
                    metadata={"provider": p.name, "query": v.text, "lang": v.language},
                )
            )
            try:
                results = await p.search(v.text, language=v.language, limit=5)
                raw.extend(results)
                info[p.name].items += len(results)
            except ProviderNotConfigured as exc:
                if info[p.name].state == "ok":
                    info[p.name].state = "not_configured"
                    info[p.name].detail = str(exc)
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_not_configured",
                        target_type=target_type_audit,
                        metadata={"provider": p.name},
                    )
                )
            except ProviderAuthRequired as exc:
                if info[p.name].state == "ok":
                    info[p.name].state = "auth_required"
                    info[p.name].detail = str(exc)
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_auth_required",
                        target_type=target_type_audit,
                        metadata={"provider": p.name},
                    )
                )
            except ProviderUnavailable as exc:
                # Upstream account-level denial — sticky on this provider
                # for this dispatch. Don't downgrade to a lesser state on
                # subsequent variants.
                info[p.name].state = "unavailable"
                info[p.name].detail = str(exc)
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_unavailable",
                        target_type=target_type_audit,
                        metadata={"provider": p.name},
                    )
                )
            except RateLimitError as exc:
                if info[p.name].state in ("ok", "not_configured", "auth_required"):
                    info[p.name].state = "rate_limited"
                    info[p.name].detail = str(exc)
                audit_service.log(
                    AuditEntryCreate(
                        action="rate_limited",
                        target_type=target_type_audit,
                        metadata={"provider": p.name},
                    )
                )
            except Exception as exc:  # noqa: BLE001
                info[p.name].state = "error"
                info[p.name].detail = f"{type(exc).__name__}: {exc}"
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type=target_type_audit,
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )
    return raw, list(info.values())


def _decide_state(
    providers: list[ProviderRunInfo],
    items_returned: int,
    items_stored: int,
) -> str:
    """Pick the single most-actionable state for the operator.

    Priority:
      1. items_stored > 0           → "completed" (or "mock" if mock-only)
      2. items_returned > 0 deduped → "deduplicated"
      3. No provider returned "ok"  → most-common failure across providers
                                       (auth_required / not_configured /
                                        rate_limited / error)
      4. ≥1 provider "ok" but 0 items → "no_results" (or "provider_error"
                                          if any provider also errored)
    """
    if not providers:
        return "no_results"
    only_mock = all(p.provider.startswith("mock_") for p in providers)

    if items_stored > 0:
        return "mock" if only_mock else "completed"
    if items_returned > 0 and items_stored == 0:
        return "deduplicated"

    states = [p.state for p in providers]
    if "ok" not in states:
        # Every provider failed. Surface the dominant failure so the
        # operator sees the action they need to take, not generic "no_results".
        from collections import Counter
        return Counter(states).most_common(1)[0][0]
    if "error" in states:
        return "provider_error"
    return "no_results"


def _summary_message(
    channel: str,
    state: str,
    providers: list[ProviderRunInfo],
    items_returned: int,
    items_stored: int,
    items_deduped: int,
) -> str:
    counts = ", ".join(
        f"{p.provider}={p.state}({p.items})" for p in providers
    ) or "no providers configured"
    base = (
        f"{channel.capitalize()} dispatch: state={state}, "
        f"providers_attempted={len(providers)}, "
        f"items_returned={items_returned}, items_stored={items_stored}, "
        f"items_deduped={items_deduped}."
    )
    hints = {
        "not_configured": " No real provider keys configured for this channel; "
                          "configure backend env or enable MOCK_PROVIDERS=true.",
        "auth_required":  " All providers for this channel require OAuth — "
                          "complete the connect flow in Settings.",
        "unavailable":    " Provider(s) are CLOSED to this account/project at "
                          "the upstream level (e.g., Google Custom Search "
                          "JSON API denied). OAuth will NOT fix this — pick "
                          "a different provider or request access.",
        "rate_limited":   " All providers were rate-limited; retry later.",
        "provider_error": " At least one provider returned an error; "
                          "see the providers field for detail.",
        "deduplicated":   " Providers returned items but all duplicate the "
                          "evidence already on this case.",
        "no_results":     " Providers ran without error but returned nothing.",
        "completed":      "",
        "mock":           " Mock providers only — set real keys to query "
                          "live sources.",
    }
    return base + hints.get(state, "") + f" Per-provider: [{counts}]."


async def run_web(case: Case) -> WebSocialStartResponse:
    plan = build_query_plan(case)
    providers = get_web_search_providers()
    raw, info = await _run_channel("web", plan, providers, "web_search")
    stored, deduped = evidence_service.normalize_and_store_with_stats(case.id, raw)
    items_returned = sum(p.items for p in info)
    state = _decide_state(info, items_returned, len(stored))
    msg = _summary_message("web", state, info, items_returned, len(stored), deduped)
    audit_service.log(
        AuditEntryCreate(
            action=f"web_search_{state}",
            target_type="web_search",
            target_id=str(case.id),
            metadata={
                "providers_attempted": len(info),
                "items_returned": items_returned,
                "items_stored": len(stored),
                "items_deduped": deduped,
            },
        )
    )
    return WebSocialStartResponse(
        state=state,
        case_id=str(case.id),
        channel="web",
        providers_attempted=len(info),
        providers_with_items=sum(1 for p in info if p.items > 0),
        items_returned=items_returned,
        items_deduped=deduped,
        message=msg,
        providers=info,
        evidence=stored,
    )


async def run_social(case: Case) -> WebSocialStartResponse:
    plan = build_query_plan(case)
    providers = get_social_search_providers()
    raw, info = await _run_channel("social", plan, providers, "social_search")
    stored, deduped = evidence_service.normalize_and_store_with_stats(case.id, raw)
    items_returned = sum(p.items for p in info)
    state = _decide_state(info, items_returned, len(stored))
    msg = _summary_message("social", state, info, items_returned, len(stored), deduped)
    audit_service.log(
        AuditEntryCreate(
            action=f"social_search_{state}",
            target_type="social_search",
            target_id=str(case.id),
            metadata={
                "providers_attempted": len(info),
                "items_returned": items_returned,
                "items_stored": len(stored),
                "items_deduped": deduped,
            },
        )
    )
    return WebSocialStartResponse(
        state=state,
        case_id=str(case.id),
        channel="social",
        providers_attempted=len(info),
        providers_with_items=sum(1 for p in info if p.items > 0),
        items_returned=items_returned,
        items_deduped=deduped,
        message=msg,
        providers=info,
        evidence=stored,
    )


async def run_archive(case: Case) -> ArchiveStartResponse:
    """Strict archive dispatch: queries are anchored to URLs that already
    exist in the case's evidence (P1, P2) and host-wildcards derived from
    them (P3). No static domain list, no free-text fan-out."""
    case_evidence = get_store().list_evidence(case_id=case.id)
    targets = build_targets(case_evidence)

    if not targets:
        audit_service.log(
            AuditEntryCreate(
                action="archive_no_targets",
                target_type="archive",
                target_id=str(case.id),
                metadata={"reason": "no anchor URLs in case evidence"},
            )
        )
        return ArchiveStartResponse(
            state="no_targets",
            case_id=str(case.id),
            targets_attempted=0,
            message="No URLs in this case yet. Run a web or social search "
                    "first; archive providers will then have public URL "
                    "anchors to query.",
            evidence=[],
        )

    providers = get_archive_providers()

    # Fan out (target × provider) calls concurrently with bounded
    # concurrency. Wayback / Common Crawl per-call timeouts are
    # already set inside each provider; we cap how many we keep in
    # flight at once so the operator's dispatch returns in seconds,
    # not minutes, even when Vertex produced ~5 unique anchor URLs
    # (which expand to ~10-15 P1+P3 targets × 4 providers).
    sem = asyncio.Semaphore(8)

    async def _one(t, p) -> list[ProviderResult]:
        audit_service.log(
            AuditEntryCreate(
                action="provider_call",
                target_type="archive",
                metadata={
                    "provider": p.name,
                    "target": t.url_or_pattern,
                    "priority": t.priority,
                },
            )
        )
        async with sem:
            try:
                return await p.lookup(t.url_or_pattern, limit=5)
            except ProviderNotConfigured as exc:
                # Stub provider with no real implementation yet (e.g.
                # search_snippet). Distinct audit action so the
                # operator can tell it apart from upstream errors.
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_not_configured",
                        target_type="archive",
                        metadata={"provider": p.name, "detail": str(exc)},
                    )
                )
                return []
            except RateLimitError as exc:
                audit_service.log(
                    AuditEntryCreate(
                        action="rate_limited",
                        target_type="archive",
                        metadata={"provider": p.name, "detail": str(exc)},
                    )
                )
                return []
            except Exception as exc:  # noqa: BLE001
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type="archive",
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )
                return []

    tasks = [_one(t, p) for t in targets for p in providers]
    batches = await asyncio.gather(*tasks, return_exceptions=False)
    raw_results: list[ProviderResult] = [r for batch in batches for r in batch]

    stored = evidence_service.normalize_and_store(case.id, raw_results)
    if not stored:
        audit_service.log(
            AuditEntryCreate(
                action="archive_no_results",
                target_type="archive",
                target_id=str(case.id),
                metadata={"providers_attempted": len(providers), "targets_attempted": len(targets)},
            )
        )
        return ArchiveStartResponse(
            state="no_results",
            case_id=str(case.id),
            targets_attempted=len(targets),
            message=f"Tried {len(targets)} URL target(s) across {len(providers)} archive provider(s); no captures returned.",
            evidence=[],
        )

    return ArchiveStartResponse(
        state="completed",
        case_id=str(case.id),
        targets_attempted=len(targets),
        message=f"Archive returned {len(stored)} item(s) from {len(targets)} URL target(s).",
        evidence=stored,
    )


__all__ = [
    "run_web",
    "run_social",
    "run_archive",
    "ArchiveStartResponse",
    "WebSocialStartResponse",
    "ProviderRunInfo",
]
