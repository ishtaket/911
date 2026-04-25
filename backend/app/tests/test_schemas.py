"""Schema validation + invariants."""
from __future__ import annotations

from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.hypothesis import Hypothesis, RiskLevel
from app.schemas.person import Person
from app.schemas.provider_result import ProviderResult, SourceType
from app.schemas.validation import ValidationLevel3Action, ValidationState


def test_provider_result_defaults() -> None:
    pr = ProviderResult(provider="x", source_type=SourceType.WEB)
    assert pr.confidence == 0.0
    assert pr.is_legal_source is True


def test_evidence_default_status_is_candidate() -> None:
    person = Person(full_name="Test")
    pr = ProviderResult(provider="x", source_type=SourceType.WEB)
    from uuid import uuid4

    e = Evidence(case_id=uuid4(), source_type=pr.source_type, provider=pr.provider)
    assert e.status == EvidenceStatus.CANDIDATE
    _ = person  # keep reference


def test_validation_state_not_confirmed_until_l3_confirm() -> None:
    state = ValidationState()
    state.level1.schema_valid = True
    state.level1.url_or_source_valid = True
    state.level1.timestamp_valid = True
    state.level1.provider_response_valid = True
    state.level1.content_hash_stored = True
    state.level1.duplicate_check_passed = True
    state.level1.legal_source = True
    state.level2.is_passed = True
    assert state.is_corroborated is True
    # still NOT human-confirmed:
    assert state.is_human_confirmed is False
    state.level3.action = ValidationLevel3Action.CONFIRM
    assert state.is_human_confirmed is True


def test_hypothesis_risk_default() -> None:
    from uuid import uuid4

    h = Hypothesis(case_id=uuid4(), label="x")
    assert h.risk == RiskLevel.MEDIUM
