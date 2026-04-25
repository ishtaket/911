# CLAUDE.md — Project Operating System

You are Claude Code working inside this repository.

## Project

Israel-focused OSINT + GeoINT missing-person search platform.
Working name: **Rescue911 OSINT**.

## Architecture (Android-first, server-ready)

```
Android App  →  Secure Backend API  →  OSINT / GeoINT / Social / Archive providers
                       ↓
              Evidence / Hypothesis / Validation / Audit storage
```

- The Android app is the primary operator UI (field volunteers, analysts).
- The backend is the secure OSINT/GeoINT brain and storage.
- A future web dashboard is optional — not the priority.
- Local-first development on Windows; server-ready deployment via Docker / CI.

## Mission

Build a lawful, evidence-based, multilingual platform for missing-person cases inside Israel. The platform must support English, Hebrew, and Russian, and must search public social networks, public search engines, indexed/archive sources, GeoINT image/video clues, and Israel-specific public sources.

## Hard safety boundaries

- Use only public, lawful, permissioned, or API-accessible data.
- Never hack, bypass login, bypass privacy, use stolen/leaked data, evade rate limits, or impersonate people.
- Never automatically contact people.
- Never present a lead as fact without 3-level validation.
- Sensitive personal data must be minimized, protected, access-controlled, and auditable.
- Every conclusion must be evidence-based and explainable.
- Android must never store external provider API keys; it talks only to our backend.
- Sensitive evidence and audit data live on the backend.

## Main product

A serious, trustworthy, accessible, professional rescue-themed Android dashboard. Heroic / Marvel-inspired but not childish: dark operational palette, emergency red, deep navy, gold accents, high contrast, 1–2 tap workflows.

## Languages

- English
- Hebrew (RTL layout)
- Russian
- Arabic place-name variants where useful (queries only)

## Primary geography

Israel only by default. Search queries, GeoINT, maps, locations, place names, and social-source weighting must prioritize Israel.

## Core modules

1. Case Intake
2. Query Builder
3. Web Search Connectors
4. Social Search Connectors
5. Archive / Deleted Indexed Information Search
6. GeoINT Media Analysis
7. Evidence Normalization
8. Entity Resolution
9. Hypothesis Engine
10. Three-Level Validation
11. Multilingual UI (Android)
12. Audit / Privacy / Security

## Stacks

**Android:** Kotlin, Jetpack Compose, Material 3, Coroutines, Flow, Hilt (or clean manual DI), Room, DataStore, Retrofit/OkHttp, WorkManager, Coil, AndroidX Navigation Compose, MapLibre Android (or Maps abstraction), JUnit, Compose UI tests, ADB smoke scripts. Package: `com.rescue911.osint`.

**Backend:** Python 3.12, FastAPI, Pydantic v2, SQLAlchemy 2.x, Alembic, PostgreSQL + PostGIS, Redis, httpx, Celery / Dramatiq / simple worker abstraction, S3/MinIO, OpenSearch (optional / mock), Qdrant (optional / mock), pytest, pytest-asyncio, respx.

**Infra:** Docker Compose (local / staging / prod), nginx (optional), GitHub Actions CI, PowerShell scripts for Windows local dev.

## Validation rule

Every milestone must include:
1. Unit tests
2. Integration or mocked-provider tests
3. Smoke test / E2E check
4. Documentation update
5. Security / legal boundary check

## Three-level validation

- **Level 1 — Automated:** schema, URL/source, timestamp, content hash, dedupe, legal source check.
- **Level 2 — Cross-source:** independent corroboration (social + archive, OCR + map POI, EXIF + visual clue, etc.).
- **Level 3 — Human:** confirm / reject / needs_more_checks / escalate / contact_manually. **No hypothesis is "Confirmed" without Level 3.**

## Execution rules

- Inspect the repository before changing code; do not assume structure.
- Keep changes small, testable, documented.
- Prefer working mocks over waiting for API keys.
- Prefer minimal working skeleton over over-engineered broken structure.
- Do not delete existing user files.
- Do not commit secrets.
- Do not claim tests passed unless actually run.
- If blocked, document the blocker in `docs/FINAL_REPORT.md` and continue building everything else.
