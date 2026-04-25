"""In-memory store — placeholder until Postgres wiring is finished."""
from __future__ import annotations

from threading import RLock
from uuid import UUID

from app.schemas.audit import AuditEntry
from app.schemas.case import Case
from app.schemas.evidence import Evidence
from app.schemas.geoint import GeoIntResult
from app.schemas.hypothesis import Hypothesis


class InMemoryStore:
    def __init__(self) -> None:
        self._lock = RLock()
        self.cases: dict[UUID, Case] = {}
        self.evidence: dict[UUID, Evidence] = {}
        self.hypotheses: dict[UUID, Hypothesis] = {}
        self.geoint_results: dict[UUID, GeoIntResult] = {}
        self.audit: list[AuditEntry] = []

    def add_case(self, case: Case) -> Case:
        with self._lock:
            self.cases[case.id] = case
            return case

    def get_case(self, case_id: UUID) -> Case | None:
        with self._lock:
            return self.cases.get(case_id)

    def list_cases(self) -> list[Case]:
        with self._lock:
            return list(self.cases.values())

    def add_evidence(self, e: Evidence) -> Evidence:
        with self._lock:
            self.evidence[e.id] = e
            return e

    def list_evidence(self, case_id: UUID | None = None) -> list[Evidence]:
        with self._lock:
            items = list(self.evidence.values())
            if case_id is not None:
                items = [e for e in items if e.case_id == case_id]
            return items

    def get_evidence(self, evidence_id: UUID) -> Evidence | None:
        with self._lock:
            return self.evidence.get(evidence_id)

    def add_hypothesis(self, h: Hypothesis) -> Hypothesis:
        with self._lock:
            self.hypotheses[h.id] = h
            return h

    def list_hypotheses(self, case_id: UUID | None = None) -> list[Hypothesis]:
        with self._lock:
            items = list(self.hypotheses.values())
            if case_id is not None:
                items = [h for h in items if h.case_id == case_id]
            return items

    def add_geoint_result(self, r: GeoIntResult) -> GeoIntResult:
        with self._lock:
            self.geoint_results[r.id] = r
            return r

    def get_geoint_result(self, job_id: UUID) -> GeoIntResult | None:
        with self._lock:
            return self.geoint_results.get(job_id)

    def add_audit(self, entry: AuditEntry) -> AuditEntry:
        with self._lock:
            self.audit.append(entry)
            return entry

    def list_audit(self, limit: int = 200) -> list[AuditEntry]:
        with self._lock:
            return list(reversed(self.audit[-limit:]))


_store = InMemoryStore()


def get_store() -> InMemoryStore:
    return _store
