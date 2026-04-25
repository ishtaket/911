"""Hypothesis engine — ranks GeoINT candidates and links evidence."""
from __future__ import annotations

from uuid import UUID

from app.schemas.geoint import GeoIntCandidate, GeoIntResult
from app.schemas.hypothesis import Hypothesis, HypothesisCreate, RiskLevel
from app.services.store import get_store


def _risk_from_confidence(c: float) -> RiskLevel:
    if c >= 0.75:
        return RiskLevel.LOW
    if c >= 0.5:
        return RiskLevel.MEDIUM
    if c >= 0.25:
        return RiskLevel.HIGH
    return RiskLevel.CRITICAL


def hypotheses_from_geoint(case_id: UUID, result: GeoIntResult) -> list[Hypothesis]:
    store = get_store()
    out: list[Hypothesis] = []
    for cand in sorted(result.candidates, key=lambda c: c.confidence, reverse=True):
        h = Hypothesis(
            **HypothesisCreate(
                case_id=case_id,
                hypothesis_type="location",
                label=cand.place_name or f"({cand.lat:.4f}, {cand.lon:.4f})",
                lat=cand.lat,
                lon=cand.lon,
                place_name=cand.place_name,
                confidence=cand.confidence,
                contradictions=list(cand.contradictions),
                risk=_risk_from_confidence(cand.confidence),
                next_checks=list(cand.next_checks),
            ).model_dump()
        )
        out.append(store.add_hypothesis(h))
    return out


def rank_candidates(candidates: list[GeoIntCandidate]) -> list[GeoIntCandidate]:
    return sorted(candidates, key=lambda c: c.confidence, reverse=True)
