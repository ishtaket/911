# Deployment

## Environments

| Env | Compose file | Notes |
| --- | --- | --- |
| local | `infra/docker-compose.local.yml` | Mock providers; in-memory store; Postgres+PostGIS optional |
| staging | `infra/docker-compose.staging.yml` | Real keys via `.env.staging`; nginx; restart unless-stopped |
| production | TODO | Suggested: orchestrated (k8s) or compose on hardened VM |

## Required env (staging / prod)

See `.env.example` for the full list. At minimum: `DATABASE_URL`, `REDIS_URL`, `S3_*`, plus the API keys for whichever providers you want to enable. Set `MOCK_PROVIDERS=false` once real keys are configured (otherwise the backend will keep using mocks).

## Backend container

`backend/Dockerfile` (Python 3.12-slim):
- Copies app/, installs requirements, exposes 8000.
- Health check: `GET /v1/health`.

## Database

PostgreSQL 16 + PostGIS 3.4. Initial milestone uses the in-memory store; milestone 2 will wire SQLAlchemy + Alembic and ship migrations.

## Object storage

MinIO locally (S3-compatible); for staging/prod use S3 / GCS / Azure Blob behind the same env vars.

## TLS

Terminate at nginx (staging) or at the cloud load balancer (production). Backend speaks HTTP internally.

## Backup

- Postgres: nightly `pg_dump`, retained 30 days, encrypted.
- Object storage: versioning + lifecycle rules.
- Audit log: ship to write-once storage.

## Rollout

- Tag releases on `main`.
- CI builds backend Docker image and Android `assembleRelease` (signed in CI with secret keystore — TODO).
- Promote per environment via image tag.

## Rollback

- Backend: redeploy the prior image tag.
- Android: Play Store staged rollout — halt or revert via console.
- DB: only forward migrations; rollback via `pg_restore` from the latest backup.
