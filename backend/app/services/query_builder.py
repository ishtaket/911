"""Query builder — Israel-aware, multilingual variants."""
from __future__ import annotations

from app.schemas.case import Case
from app.schemas.query import QueryChannel, QueryPlan, QueryVariant


HEBREW_MISSING_PREFIXES = ["נעדר", "נעדרת"]
RUSSIAN_MISSING_PREFIXES = ["пропал", "пропала", "разыскивается"]
ARABIC_MISSING_PREFIXES = ["مفقود", "مفقودة"]


def _name_variants(case: Case) -> list[str]:
    base = [case.person.full_name]
    base.extend(v for v in case.person.name_variants if v)
    return [v for v in base if v]


def _city_hint(case: Case) -> str:
    return case.last_seen_location or "Israel"


def build_query_plan(case: Case) -> QueryPlan:
    """Generate a QueryPlan with EN/HE/RU/AR variants across channels."""
    names = _name_variants(case)
    city = _city_hint(case)

    variants: list[QueryVariant] = []

    # English variants
    for name in names:
        variants.append(QueryVariant(text=f'"{name}" missing person Israel', language="en", channel=QueryChannel.WEB))
        variants.append(QueryVariant(text=f'"{name}" {city}', language="en", channel=QueryChannel.SOCIAL))
        variants.append(QueryVariant(text=f'"{name}" missing', language="en", channel=QueryChannel.ARCHIVE))

    # Hebrew variants
    for name in names:
        for prefix in HEBREW_MISSING_PREFIXES:
            variants.append(QueryVariant(text=f"{prefix} {name} {city}", language="he", channel=QueryChannel.WEB))
            variants.append(QueryVariant(text=f"{prefix} {name}", language="he", channel=QueryChannel.SOCIAL))

    # Russian variants
    for name in names:
        for prefix in RUSSIAN_MISSING_PREFIXES:
            variants.append(QueryVariant(text=f"{prefix} {name} Израиль", language="ru", channel=QueryChannel.WEB))
            variants.append(QueryVariant(text=f"{prefix} {name}", language="ru", channel=QueryChannel.SOCIAL))

    # Arabic place-name variants (queries only — UI does not include Arabic)
    for name in names:
        for prefix in ARABIC_MISSING_PREFIXES:
            variants.append(QueryVariant(text=f"{prefix} {name}", language="ar", channel=QueryChannel.WEB, weight=0.5))

    # Maps geocoding
    if case.last_seen_location:
        variants.append(
            QueryVariant(text=case.last_seen_location, language="en", channel=QueryChannel.MAPS, weight=0.8)
        )

    return QueryPlan(case_id=case.id, variants=variants, region="IL")
