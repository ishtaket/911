"""Person schemas — minimal, privacy-aware profile."""
from __future__ import annotations

from datetime import date
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


class PersonCreate(BaseModel):
    full_name: str = Field(..., min_length=1, max_length=200)
    name_variants: list[str] = Field(default_factory=list)
    birth_date: date | None = None
    age: int | None = None
    gender: str | None = None
    nationality: str | None = None
    languages_spoken: list[str] = Field(default_factory=list)
    description: str | None = None
    photo_urls: list[str] = Field(default_factory=list)
    social_handles: list[str] = Field(default_factory=list)
    last_known_address: str | None = None
    notes: str | None = None


class Person(PersonCreate):
    id: UUID = Field(default_factory=uuid4)
