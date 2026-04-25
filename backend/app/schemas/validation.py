"""Three-level validation schemas.

Level 1: automated technical validation
Level 2: cross-source corroboration
Level 3: human review

Rule: nothing is "Confirmed" without Level 3 human action.
"""
from __future__ import annotations

from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field


class ValidationLevel1(BaseModel):
    schema_valid: bool = False
    url_or_source_valid: bool = False
    timestamp_valid: bool = False
    provider_response_valid: bool = False
    content_hash_stored: bool = False
    duplicate_check_passed: bool = False
    legal_source: bool = False

    @property
    def is_passed(self) -> bool:
        return all(
            (
                self.schema_valid,
                self.url_or_source_valid,
                self.timestamp_valid,
                self.provider_response_valid,
                self.content_hash_stored,
                self.duplicate_check_passed,
                self.legal_source,
            )
        )


class ValidationLevel2(BaseModel):
    independent_sources: int = 0  # number of independent sources agreeing
    cross_signals: list[str] = Field(default_factory=list)
    is_passed: bool = False  # True when ≥2 independent sources corroborate


class ValidationLevel3Action(StrEnum):
    PENDING = "pending"
    CONFIRM = "confirm"
    REJECT = "reject"
    NEEDS_MORE_CHECKS = "needs_more_checks"
    ESCALATE_TO_AUTHORITIES = "escalate_to_authorities"
    CONTACT_MANUALLY = "contact_manually"


class ValidationLevel3(BaseModel):
    action: ValidationLevel3Action = ValidationLevel3Action.PENDING
    reviewer_id: str | None = None
    reviewed_at: datetime | None = None
    note: str | None = None


class ValidationState(BaseModel):
    level1: ValidationLevel1 = Field(default_factory=ValidationLevel1)
    level2: ValidationLevel2 = Field(default_factory=ValidationLevel2)
    level3: ValidationLevel3 = Field(default_factory=ValidationLevel3)

    @property
    def is_human_confirmed(self) -> bool:
        return self.level3.action == ValidationLevel3Action.CONFIRM

    @property
    def is_corroborated(self) -> bool:
        return self.level1.is_passed and self.level2.is_passed
