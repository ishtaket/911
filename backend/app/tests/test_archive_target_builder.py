"""Tests for the strict, evidence-anchored archive target builder."""
from __future__ import annotations

from uuid import uuid4

from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.provider_result import SourceType
from app.schemas.validation import ValidationLevel1, ValidationState
from app.services.archive_target_builder import build_targets


def _ev(**kw) -> Evidence:
    """Minimal evidence factory."""
    base = dict(
        id=uuid4(),
        case_id=uuid4(),
        source_type=SourceType.WEB,
        provider="test",
        title=None,
        url=None,
        snippet=None,
        language="en",
        confidence=0.5,
        risk_flags=[],
        content_hash=None,
        status=EvidenceStatus.CANDIDATE,
        validation=ValidationState(level1=ValidationLevel1()),
        next_action=None,
    )
    base.update(kw)
    return Evidence(**base)


def test_empty_evidence_returns_no_targets():
    assert build_targets([]) == []


def test_p1_takes_evidence_url_verbatim():
    e = _ev(url="https://www.ynet.co.il/news/article/abc123")
    targets = build_targets([e])
    p1 = [t for t in targets if t.priority == 1]
    assert len(p1) == 1
    assert p1[0].url_or_pattern == "https://www.ynet.co.il/news/article/abc123"
    assert p1[0].is_pattern is False
    assert p1[0].anchor_evidence_id == str(e.id)


def test_p2_extracts_urls_from_snippet():
    e = _ev(
        url=None,
        snippet="See public report at https://example.com/article/1 and also https://other.org/x",
    )
    targets = build_targets([e])
    p2_urls = sorted(t.url_or_pattern for t in targets if t.priority == 2)
    assert "https://example.com/article/1" in p2_urls
    assert "https://other.org/x" in p2_urls


def test_p3_wildcards_only_from_anchor_hosts():
    e = _ev(url="https://www.ynet.co.il/news/article/abc123")
    targets = build_targets([e])
    p3 = sorted(t.url_or_pattern for t in targets if t.priority == 3)
    # Both the host wildcard and the www-stripped variant — anchored to the
    # same evidence URL — are allowed; nothing else.
    assert "www.ynet.co.il/*" in p3
    assert "ynet.co.il/*" in p3
    # MUST NOT broaden to apex registrable domain
    assert "co.il/*" not in p3
    # MUST NOT include unrelated hosts the static MockData / seed knew about
    assert all("facebook" not in p for p in p3)
    assert all("instagram" not in p for p in p3)


def test_p3_does_not_invent_hosts_when_no_url_present():
    e = _ev(snippet="Volunteer rescue activity in Tel Aviv. No URLs here.")
    targets = build_targets([e])
    assert all(t.priority != 3 for t in targets)
    assert targets == []


def test_priorities_preserved_and_dedup_first_wins():
    """If the same URL appears in both evidence.url AND a snippet, P1 wins."""
    e1 = _ev(url="https://example.com/x")
    e2 = _ev(snippet="see https://example.com/x for context")
    targets = build_targets([e1, e2])
    only_x = [t for t in targets if t.url_or_pattern == "https://example.com/x"]
    assert len(only_x) == 1
    assert only_x[0].priority == 1


def test_ordering_p1_then_p2_then_p3():
    e = _ev(
        url="https://a.example/page",
        snippet="related: https://b.example/other",
    )
    targets = build_targets([e])
    priorities = [t.priority for t in targets]
    assert priorities == sorted(priorities)  # non-decreasing 1,1,...,2,...,3,...


def test_non_url_strings_in_evidence_url_ignored():
    e = _ev(url="not-a-url-just-text")
    targets = build_targets([e])
    assert targets == []
