"""Three-level validation engine."""
from __future__ import annotations

from datetime import datetime
from uuid import UUID

from app.schemas.audit import AuditEntryCreate
from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.validation import ValidationLevel3, ValidationLevel3Action
from app.services import audit_service
from app.services.store import get_store


def review_evidence(
    evidence_id: UUID,
    action: ValidationLevel3Action,
    reviewer_id: str | None = None,
    note: str | None = None,
) -> Evidence | None:
    store = get_store()
    e = store.get_evidence(evidence_id)
    if e is None:
        return None
    e.validation.level3 = ValidationLevel3(
        action=action, reviewer_id=reviewer_id, reviewed_at=datetime.utcnow(), note=note
    )
    e.status = _status_from_action(action)
    e.updated_at = datetime.utcnow()

    audit_service.log(
        AuditEntryCreate(
            actor_id=reviewer_id,
            action=f"review.{action.value}",
            target_type="evidence",
            target_id=str(evidence_id),
            metadata={"note": note} if note else {},
        )
    )
    return e


def _status_from_action(action: ValidationLevel3Action) -> EvidenceStatus:
    return {
        ValidationLevel3Action.PENDING: EvidenceStatus.NEEDS_REVIEW,
        ValidationLevel3Action.CONFIRM: EvidenceStatus.HUMAN_CONFIRMED,
        ValidationLevel3Action.REJECT: EvidenceStatus.REJECTED,
        ValidationLevel3Action.NEEDS_MORE_CHECKS: EvidenceStatus.NEEDS_REVIEW,
        ValidationLevel3Action.ESCALATE_TO_AUTHORITIES: EvidenceStatus.HUMAN_CONFIRMED,
        ValidationLevel3Action.CONTACT_MANUALLY: EvidenceStatus.NEEDS_REVIEW,
    }[action]
