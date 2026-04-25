# Backend API

FastAPI app at `backend/app/main.py`. All routes under `/v1`.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Backend metadata + env + mock-mode flag |
| `GET` | `/v1/health` | Health |
| `GET` | `/v1/health/ready` | Readiness |
| `POST` | `/v1/cases` | Create a case |
| `GET` | `/v1/cases` | List cases |
| `GET` | `/v1/cases/{case_id}` | Get a case |
| `POST` | `/v1/media/upload` | Upload media (multipart) |
| `POST` | `/v1/search/start/{case_id}` | Run multi-channel mocked search |
| `GET` | `/v1/search/plan/{case_id}` | Inspect generated query plan |
| `POST` | `/v1/geoint/analyze` | Analyze a media item |
| `GET` | `/v1/geoint/{job_id}` | Get GeoINT result |
| `POST` | `/v1/geoint/promote/{job_id}` | Promote candidates → hypotheses |
| `GET` | `/v1/evidence` | List evidence (filter by `case_id`) |
| `GET` | `/v1/evidence/{evidence_id}` | Get evidence |
| `GET` | `/v1/hypotheses` | List hypotheses (filter by `case_id`) |
| `POST` | `/v1/review/{evidence_id}` | Level-3 human action |
| `GET` | `/v1/audit` | Audit log (RBAC TODO) |

OpenAPI/Swagger UI: `http://localhost:8000/docs`.

## Schemas

In `backend/app/schemas/`:

- `case.py` — `Case`, `CaseCreate`, `CaseUpdate`, `CaseStatus`
- `person.py` — `Person`, `PersonCreate`
- `query.py` — `QueryPlan`, `QueryVariant`, `QueryChannel`
- `provider_result.py` — `ProviderResult`, `SourceType`
- `evidence.py` — `Evidence`, `EvidenceCreate`, `EvidenceStatus`
- `hypothesis.py` — `Hypothesis`, `HypothesisCreate`, `RiskLevel`
- `validation.py` — `ValidationLevel1`, `ValidationLevel2`, `ValidationLevel3`, `ValidationLevel3Action`, `ValidationState`
- `audit.py` — `AuditEntry`, `AuditEntryCreate`
- `geoint.py` — `GeoIntAnalyzeRequest`, `GeoIntCandidate`, `GeoIntResult`, `GeoTag`

## Services

In `backend/app/services/`:

- `query_builder.py` — Israel-aware EN/HE/RU/AR query plan
- `evidence_service.py` — normalization, dedup by content hash, L1 validation
- `hypothesis_service.py` — ranking, mapping GeoINT candidates → hypotheses
- `validation_service.py` — L3 review actions
- `search_service.py` — orchestrates web/social/archive providers
- `geoint_service.py` — orchestrates GeoINT providers, merges candidates
- `audit_service.py` — append-only audit log
- `store.py` — in-memory store (replace with SQLAlchemy in milestone 2)

## Provider registry

`backend/app/providers/registry.py` returns the right concrete provider list based on configured env (or mock fallback). See `docs/SOCIAL_SEARCH.md`, `docs/ARCHIVE_SEARCH.md`, `docs/GEOINT_PIPELINE.md` for per-provider details.
