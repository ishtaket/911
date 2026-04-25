# Rescue911 OSINT — Israel Missing-Person Search Platform

A lawful, evidence-based, multilingual platform for missing-person cases inside Israel, built **Android-first, server-ready**.

```
Android operator UI  →  FastAPI backend  →  Public OSINT / GeoINT providers
                              ↓
                  Postgres+PostGIS / Redis / S3 / OpenSearch / Qdrant
```

The Android app is the primary operator UI (volunteers, analysts).
The backend is the secure OSINT/GeoINT brain and storage.

## Status (milestone 1)

- ✅ Android Kotlin/Compose scaffold (`com.rescue911.osint`) — 16 screens, theme, navigation, mock data, EN/HE/RU strings, JUnit + Compose UI tests.
- ✅ FastAPI backend with health, cases, search, geoint, evidence, hypotheses, review, audit endpoints — 19 tests pass.
- ✅ Provider interfaces + mock providers for web (Brave/CSE/SerpAPI), social (FB/IG/TT/YT/TG/Reddit/X/VK/LinkedIn), archive (Wayback/CommonCrawl/snippet/mirror), GeoINT (EXIF/OCR/Vision/GeoSeer/Picarta/OpenAI), maps (Google/LocationIQ/OSM/Sentinel).
- ✅ Three-level validation engine (L1 automated → L2 cross-source → L3 human).
- ✅ Audit log on every external provider call and L3 review.
- ✅ `infra/docker-compose.local.yml` (Postgres+PostGIS, Redis, MinIO, optional OpenSearch / Qdrant; backend container).
- ✅ PowerShell scripts for env check, Docker, backend, Android build/install/smoke, scaffold verification.
- ✅ GitHub Actions CI (backend pytest; Android lint check).

## Hard safety boundaries

- Public, lawful, permissioned, or API-accessible data only.
- No hacking, login bypass, leaked data, rate-limit evasion, fake identity, or automated contact.
- Sensitive personal data is minimized, protected, access-controlled, audit-logged.
- No "found" without **Level 3 human confirmation**.

## Quick start (Windows)

```powershell
# 1. Verify your machine has the prerequisites:
.\scripts\check_local_environment.ps1

# 2. Bring up infra (Postgres+PostGIS, Redis, MinIO):
.\scripts\dev_docker_up.ps1

# 3. Run backend in dev mode:
.\scripts\dev_backend.ps1
# → http://localhost:8011/v1/health  (Rescue911 dedicated local port; 8000 is reserved)

# 4. Run backend tests:
.\scripts\dev_backend_test.ps1

# 5. Build the Android app (requires JDK 17 + Android SDK):
.\scripts\dev_android_build.ps1
.\scripts\dev_android_smoke.ps1   # installs + launches + checks logcat
```

## Repository layout

```
.
├── android/              # Kotlin/Compose app (com.rescue911.osint)
├── backend/              # FastAPI backend
├── infra/                # Docker compose + nginx skeleton
├── scripts/              # PowerShell scripts for Windows local dev
├── docs/                 # Specs, runbooks, legal/privacy/security
├── .claude/              # Claude Code agents, skills, prompts, ROUTER
├── .github/workflows/    # CI
├── CLAUDE.md             # Project operating system for Claude Code
├── .env.example          # Backend config template (no secrets)
└── README.md
```

## Languages

- **English** (default)
- **Hebrew** with full RTL layout (`values-iw/`)
- **Russian** (`values-ru/`)
- Arabic place-name variants are used in backend query generation only.

## Documentation

- [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/ANDROID_APP.md`](docs/ANDROID_APP.md)
- [`docs/BACKEND_API.md`](docs/BACKEND_API.md)
- [`docs/GEOINT_PIPELINE.md`](docs/GEOINT_PIPELINE.md)
- [`docs/SOCIAL_SEARCH.md`](docs/SOCIAL_SEARCH.md)
- [`docs/ARCHIVE_SEARCH.md`](docs/ARCHIVE_SEARCH.md)
- [`docs/LEGAL_BOUNDARIES.md`](docs/LEGAL_BOUNDARIES.md)
- [`docs/PRIVACY.md`](docs/PRIVACY.md)
- [`docs/SECURITY.md`](docs/SECURITY.md)
- [`docs/TESTING.md`](docs/TESTING.md)
- [`docs/RUNBOOK.md`](docs/RUNBOOK.md)
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)
- [`docs/API_KEYS.md`](docs/API_KEYS.md)
- [`docs/LOCAL_ENVIRONMENT.md`](docs/LOCAL_ENVIRONMENT.md)
- [`docs/FINAL_REPORT.md`](docs/FINAL_REPORT.md) — what's built, what's mocked, what's blocked.

## Contributing

This is a sensitive domain. Read `CLAUDE.md`, `docs/LEGAL_BOUNDARIES.md`, and `docs/PRIVACY.md` before opening a PR. Every milestone must include unit tests, mocked-provider integration tests, smoke / E2E checks, doc updates, and a security/legal review note.
