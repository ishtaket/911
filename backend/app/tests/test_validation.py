"""Validation engine tests."""
from __future__ import annotations

from uuid import uuid4

from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.validation import ValidationLevel3Action
from app.services import validation_service
from app.services.store import get_store


def setup_function() -> None:
    store = get_store()
    store.evidence.clear()
    store.audit.clear()


def _make_evidence() -> Evidence:
    e = Evidence(case_id=uuid4(), source_type="web", provider="mock")
    return get_store().add_evidence(e)


def test_review_confirm_marks_human_confirmed() -> None:
    e = _make_evidence()
    out = validation_service.review_evidence(
        e.id, ValidationLevel3Action.CONFIRM, reviewer_id="op", note="checked"
    )
    assert out is not None
    assert out.status == EvidenceStatus.HUMAN_CONFIRMED
    assert out.validation.is_human_confirmed is True


def test_review_reject_marks_rejected() -> None:
    e = _make_evidence()
    out = validation_service.review_evidence(e.id, ValidationLevel3Action.REJECT, reviewer_id="op")
    assert out is not None
    assert out.status == EvidenceStatus.REJECTED


def test_review_unknown_evidence_returns_none() -> None:
    out = validation_service.review_evidence(uuid4(), ValidationLevel3Action.CONFIRM)
    assert out is None
