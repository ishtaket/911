"""GeoINT schemas — image/video clue analysis."""
from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


class GeoTag(BaseModel):
    lat: float
    lon: float
    accuracy_m: float | None = None
    source: str = "exif"  # "exif" | "vision" | "geoseer" | "picarta" | "user"


class GeoIntAnalyzeRequest(BaseModel):
    case_id: UUID
    media_id: UUID
    languages: list[str] = Field(default_factory=lambda: ["en", "he", "ru", "ar"])
    region_hint: str = "IL"


class GeoIntCandidate(BaseModel):
    """A ranked location candidate with evidence and contradictions."""

    lat: float
    lon: float
    place_name: str | None = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    evidence: list[str] = Field(default_factory=list)  # human-readable evidence chips
    contradictions: list[str] = Field(default_factory=list)
    risk: str | None = None
    next_checks: list[str] = Field(default_factory=list)
    sources: list[str] = Field(default_factory=list)  # provider names


class GeoIntResult(BaseModel):
    id: UUID = Field(default_factory=uuid4)
    case_id: UUID
    media_id: UUID
    candidates: list[GeoIntCandidate] = Field(default_factory=list)
    exif_geotag: GeoTag | None = None
    extracted_text: list[str] = Field(default_factory=list)  # OCR
    detected_landmarks: list[str] = Field(default_factory=list)
    finished_at: datetime = Field(default_factory=datetime.utcnow)
