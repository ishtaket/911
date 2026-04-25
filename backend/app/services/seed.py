"""Seed Israel-themed sample data so the Android UI has content out of the box.

Idempotent: re-running the seed when cases already exist is a no-op.
This is a development convenience only; staging/production will load from
the persistent store, not from this file.
"""
from __future__ import annotations

from datetime import datetime, timedelta
from uuid import UUID

from app.schemas.case import Case, CaseStatus
from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.hypothesis import Hypothesis, RiskLevel
from app.schemas.person import Person
from app.schemas.provider_result import SourceType
from app.schemas.validation import (
    ValidationLevel1,
    ValidationLevel2,
    ValidationLevel3,
    ValidationLevel3Action,
    ValidationState,
)
from app.services.store import get_store

# Stable UUIDs so the Android app sees the same IDs across restarts.
CASE_1 = UUID("11111111-1111-4111-8111-111111111111")
CASE_2 = UUID("22222222-2222-4222-8222-222222222222")
CASE_3 = UUID("33333333-3333-4333-8333-333333333333")
PERSON_1 = UUID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1")
PERSON_2 = UUID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2")
PERSON_3 = UUID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3")
EV_1 = UUID("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1")
EV_2 = UUID("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2")
EV_3 = UUID("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3")
HYP_1 = UUID("66666666-6666-4666-8666-666666666661")
HYP_2 = UUID("66666666-6666-4666-8666-666666666662")


def _l1_pass() -> ValidationLevel1:
    return ValidationLevel1(
        schema_valid=True,
        url_or_source_valid=True,
        timestamp_valid=True,
        provider_response_valid=True,
        content_hash_stored=True,
        duplicate_check_passed=True,
        legal_source=True,
    )


def seed_if_empty() -> None:
    store = get_store()
    if store.list_cases():
        return  # idempotent

    now = datetime.utcnow()

    cases = [
        Case(
            id=CASE_1,
            title="Anna L.",
            description="Last seen near Tel Aviv promenade, blue jacket.",
            status=CaseStatus.INVESTIGATING,
            person=Person(
                id=PERSON_1,
                full_name="Anna Lifshitz",
                name_variants=["אנה ליפשיץ", "Анна Лифшиц"],
                age=27,
                languages_spoken=["he", "ru", "en"],
            ),
            last_seen_at=now - timedelta(days=3),
            last_seen_location="Tel Aviv-Yafo",
            last_seen_lat=32.0853,
            last_seen_lon=34.7818,
            languages=["en", "he", "ru"],
        ),
        Case(
            id=CASE_2,
            title="Yossi B.",
            description="Hiker missing in Galilee region.",
            status=CaseStatus.HUMAN_REVIEW,
            person=Person(
                id=PERSON_2,
                full_name="Yossi Ben-David",
                name_variants=["יוסי בן דוד", "Йосси Бен-Давид"],
                age=54,
                languages_spoken=["he", "en"],
            ),
            last_seen_at=now - timedelta(days=2),
            last_seen_location="Mount Meron trail",
            last_seen_lat=32.997,
            last_seen_lon=35.408,
            languages=["en", "he"],
        ),
        Case(
            id=CASE_3,
            title="Dina K.",
            description="Possible mental-health vulnerability; family searching.",
            status=CaseStatus.OPEN,
            person=Person(
                id=PERSON_3,
                full_name="Dina Kohen",
                name_variants=["דינה כהן", "Дина Коэн"],
                age=19,
                languages_spoken=["he", "ru"],
            ),
            last_seen_at=now - timedelta(days=1),
            last_seen_location="Haifa central bus station",
            last_seen_lat=32.819,
            last_seen_lon=34.997,
            languages=["en", "he", "ru"],
        ),
    ]
    for c in cases:
        store.add_case(c)

    evidence = [
        Evidence(
            id=EV_1,
            case_id=CASE_1,
            source_type=SourceType.SOCIAL,
            provider="telegram_public",
            title="Public sighting on volunteer channel",
            url="https://t.me/example/123",
            snippet="Photo with timestamp matching last-seen window.",
            language="he",
            confidence=0.62,
            status=EvidenceStatus.NEEDS_REVIEW,
            validation=ValidationState(
                level1=_l1_pass(),
                level2=ValidationLevel2(independent_sources=1),
            ),
            next_action="Cross-check with archive snapshot",
        ),
        Evidence(
            id=EV_2,
            case_id=CASE_1,
            source_type=SourceType.GEOINT,
            provider="geoseer",
            title="Image-geo candidate near promenade",
            snippet="Coastline match, low confidence.",
            confidence=0.35,
            risk_flags=["low_confidence"],
            status=EvidenceStatus.CANDIDATE,
            validation=ValidationState(level1=_l1_pass()),
            next_action="Validate POI on maps provider",
        ),
        Evidence(
            id=EV_3,
            case_id=CASE_2,
            source_type=SourceType.ARCHIVE,
            provider="wayback_cdx",
            title="Public hiking forum thread (archived)",
            url="https://web.archive.org/web/example",
            snippet="User mentioned planning Meron route.",
            language="ru",
            confidence=0.45,
            risk_flags=["archive_only"],
            status=EvidenceStatus.CORROBORATED,
            validation=ValidationState(
                level1=_l1_pass(),
                level2=ValidationLevel2(independent_sources=2, is_passed=True),
                level3=ValidationLevel3(action=ValidationLevel3Action.PENDING),
            ),
            next_action="Operator confirm",
        ),
    ]
    for e in evidence:
        store.add_evidence(e)

    hypotheses = [
        Hypothesis(
            id=HYP_1,
            case_id=CASE_1,
            label="Tel Aviv-Yafo promenade",
            lat=32.0853,
            lon=34.7818,
            place_name="Tel Aviv-Yafo",
            confidence=0.55,
            evidence_ids=[EV_1, EV_2],
            contradictions=["EXIF timestamp drift: ±90min"],
            risk=RiskLevel.MEDIUM,
            next_checks=["Street-view confirm", "Second sighting"],
        ),
        Hypothesis(
            id=HYP_2,
            case_id=CASE_2,
            label="Mount Meron trail north fork",
            lat=32.997,
            lon=35.408,
            place_name="Mount Meron",
            confidence=0.42,
            evidence_ids=[EV_3],
            risk=RiskLevel.HIGH,
            next_checks=["Trail camera images", "Park ranger contact"],
        ),
    ]
    for h in hypotheses:
        store.add_hypothesis(h)
