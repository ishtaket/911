# Architecture

## Topology (Android-first, server-ready)

```
+---------------------+        HTTPS / JSON         +---------------------+
|  Android App        |  ───────────────────────▶   |  Backend API        |
|  com.rescue911.osint|     (auth token, JSON)      |  FastAPI / Python   |
+----------┬----------+                              +-----┬---------------+
           │ Operator UI                                  │ provider orchestration
           │ Compose, M3, Coroutines                       │ evidence normalization
           │ Room/DataStore (limited cache)                │ hypothesis + validation
           ▼                                               ▼
   field operators                              +-----------------------+
                                                | Storage / Infra       |
                                                | PostgreSQL + PostGIS  |
                                                | Redis  /  S3 / MinIO  |
                                                | OpenSearch (opt)      |
                                                | Qdrant (opt)          |
                                                +-----------┬-----------+
                                                            │
                                                +-----------▼-----------+
                                                | External Providers    |
                                                | Brave / Google CSE    |
                                                | Wayback / CommonCrawl |
                                                | Vision / GeoSeer /    |
                                                | Picarta / Maps APIs   |
                                                +-----------------------+
```

## Android (operator UI)

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Async:** Kotlin Coroutines + Flow
- **DI:** Hilt (or clean manual DI)
- **Local cache:** Room (limited), DataStore (settings)
- **Networking:** Retrofit + OkHttp + kotlinx.serialization
- **Background:** WorkManager
- **Images:** Coil
- **Maps:** MapLibre Android (or abstracted Maps interface)
- **Navigation:** AndroidX Navigation Compose
- **Tests:** JUnit (unit) + Compose UI tests + ADB smoke
- **Package:** `com.rescue911.osint`
- **API keys:** *never* stored on Android; client talks only to backend.

## Backend (OSINT/GeoINT brain)

- **Language:** Python 3.12
- **API:** FastAPI + Pydantic v2
- **DB:** SQLAlchemy 2.x + Alembic + PostgreSQL + PostGIS
- **Cache/queue:** Redis
- **Object store:** S3 / MinIO
- **Search:** OpenSearch (optional) / Qdrant (optional) — mockable
- **HTTP:** httpx (async)
- **Workers:** Celery / Dramatiq / simple background-task abstraction
- **Tests:** pytest + pytest-asyncio + respx

## Core services (backend)

- `case_service`
- `query_builder`
- `web_search_service`
- `social_search_service`
- `archive_search_service`
- `geoint_service`
- `evidence_service`
- `entity_resolution_service`
- `hypothesis_service`
- `validation_service`
- `audit_service`

## Provider principle

Every external API sits behind a typed provider interface with a mock fallback. If no API key is configured, the mock provider runs and the response is normalized into the same `provider_result` schema.

## Environments

- **local** — Docker Compose, mock providers by default.
- **staging** — Docker Compose with real (test-account) keys, RBAC enforced, audit logged.
- **production** — orchestrated deploy (compose / k8s), secrets via vault, full RBAC + retention + backups.

## Future (out of immediate scope)

- Optional web dashboard (read-only or analyst console).
- SQLCipher for Android local cache.
- Region expansion beyond Israel.
