"""Query builder produces multilingual variants."""
from __future__ import annotations

from app.schemas.case import Case
from app.schemas.person import Person
from app.schemas.query import QueryChannel
from app.services.query_builder import build_query_plan


def _case(name: str = "Иван Петров", city: str = "Tel Aviv") -> Case:
    return Case(
        title="Test",
        person=Person(full_name=name, name_variants=["Ivan Petrov", "איוון פטרוב"]),
        last_seen_location=city,
    )


def test_query_plan_has_languages() -> None:
    plan = build_query_plan(_case())
    langs = {v.language for v in plan.variants}
    assert {"en", "he", "ru"}.issubset(langs)


def test_query_plan_includes_channels() -> None:
    plan = build_query_plan(_case())
    channels = {v.channel for v in plan.variants}
    assert QueryChannel.WEB in channels
    assert QueryChannel.SOCIAL in channels
    assert QueryChannel.ARCHIVE in channels


def test_query_plan_uses_city_when_present() -> None:
    plan = build_query_plan(_case(city="Haifa"))
    assert any("Haifa" in v.text for v in plan.variants)
