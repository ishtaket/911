"""SQLAlchemy engine + session skeleton.

Currently unused in milestone 1 — the in-memory store handles everything. Wire
this up in milestone 2 alongside Alembic migrations.
"""
from __future__ import annotations

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import get_settings


class Base(DeclarativeBase):
    """Declarative base for all ORM models (TODO: actual models)."""


def make_engine() -> object:
    settings = get_settings()
    return create_engine(settings.database_url, future=True, pool_pre_ping=True)


def make_session_factory(engine: object) -> object:
    return sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
