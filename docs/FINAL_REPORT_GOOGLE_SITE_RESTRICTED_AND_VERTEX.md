# FINAL REPORT — Google Site Restricted + Vertex AI Search

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`
**Backend:** 127.0.0.1:8011

## Official docs reviewed

| Page | Key finding |
|---|---|
| `developers.google.com/custom-search/v1/site_restricted_api` | **"endpoints will cease to serve traffic on January 8, 2025"** + "migrate to Google Cloud's Vertex AI Search" |
| `developers.google.com/custom-search/v1/overview` | "The Custom Search JSON API is closed to new customers. Existing customers have until January 1, 2027 to transition to alternatives." API-key-only auth. |
| `cloud.google.com/generative-ai-app-builder/docs/preview-search-results` | Endpoint path `projects/{p}/locations/{l}/collections/{c}/engines/{e}/servingConfigs/{sc}:search`. Auth: SA / ADC / API-key (`searchLite`). Page sizes: 25 for websites. |
| `cloud.google.com/.../servingConfigs/searchLite` (REST ref) | `searchLite` is the **API-key-authenticated** method, restricted to **public-website data stores** — explicit migration target for the retired Site Restricted JSON API. |
| `cloud.google.com/.../create-data-store-es` | Console steps for a public-website data store. |
| `cloud.google.com/python/docs/reference/discoveryengine/latest` | Python client `google-cloud-discoveryengine`, classes `SearchServiceClient` / `SearchServiceAsyncClient`, scope `https://www.googleapis.com/auth/cloud-platform`. |

## Decision

| Path | Implemented as | Why |
|---|---|---|
| Custom Search JSON API | `google_cse` (kept) — surfaces `unavailable` from real 403 PERMISSION_DENIED | Closed to new customers; project-level denial; OAuth doesn't help |
| Custom Search Site Restricted JSON API | `google_cse_site_restricted` — raises `ProviderUnavailable` immediately (no HTTP call) unless explicit enable flag is set | **Retired by Google 2025-01-08.** No point dialing a dead endpoint. |
| Programmable Search Element | `google_programmable_search_element` (existing) — `manual_ui_required` | UI/JavaScript widget, not a backend API |
| Vertex AI Search `searchLite` | **`vertex_ai_search` (NEW, real implementation)** — POST to `discoveryengine.googleapis.com/v1/{servingConfig}:searchLite?key=…` | Official Google migration path; API-key auth; separate product, not subject to the CSE PERMISSION_DENIED gate |

## Env vars (new in `.env.example`)

```
GOOGLE_CSE_SITE_RESTRICTED_ENABLED=false
VERTEX_AI_SEARCH_ENABLED=false
VERTEX_AI_PROJECT_ID=
VERTEX_AI_LOCATION=global
VERTEX_AI_COLLECTION=default_collection
VERTEX_AI_ENGINE_ID=
VERTEX_AI_SERVING_CONFIG=default_search
VERTEX_AI_API_KEY=                 # falls back to GOOGLE_CSE_API_KEY
```

Console runbook for Vertex AI Search: see `docs/GOOGLE_SEARCH_PROVIDER_DECISION.md`. ~10 min to wire end-to-end.

## Provider order (post-fix)

```
1. google_cse_site_restricted    (retired)            → unavailable
2. google_cse                    (closed/denied)      → unavailable
3. vertex_ai_search              (NEW, official path) → not_configured until wired
4. google_kg_search              (entity lookup only)
5. brave_web_search              (independent fallback)
6. mock_web_search               (MOCK_PROVIDERS=true only)
```

## Provider status — `/v1/providers` (post-restart)

```
brave_web_search                       state=not_configured  auth=api_key  type=web_search
google_cse_site_restricted             state=unavailable     auth=api_key  type=site_search
google_cse                             state=connected       auth=api_key  type=web_search
google_programmable_search_element     state=manual_ui_required  auth=none type=web_search_ui_assisted
vertex_ai_search                       state=not_configured  auth=api_key  type=site_search
google_kg_search                       state=not_configured  auth=api_key  type=web_search
```

`google_cse=connected` here is the optimistic registry status (keys are
set); the actual project-level denial only manifests at dispatch time
as `unavailable`.

## Direct backend response — fresh case

**Case:** `94954149-1ffb-40d2-835e-8fa47bd6a9e0` ("Google Site Restricted Runtime Test")

`POST /v1/search/web/start/{cid}` → HTTP 200

```
state=not_configured
items_returned=0  items_deduped=0  evidence=0
providers (in order):
  [google_cse_site_restricted]   state=unavailable     items=0
    detail="Google Custom Search Site Restricted JSON API was retired
            by Google on 2025-01-08 (https://developers.google.com/
            custom-search/v1/site_restricted_api). Migrate to Vertex
            AI Search (provider_id=vertex_ai_search). Set
            GOOGLE_CSE_SITE_RESTRICTED_ENABLED=true only if Google has
            restored access for your Cloud project."
  [google_cse]                   state=unavailable     items=0
    detail="Google Custom Search JSON API is unavailable for this
            Google Cloud project. OAuth will not fix this — Custom
            Search JSON API only accepts API-key auth and the denial
            is at the project / account level."
  [vertex_ai_search]             state=not_configured  items=0
    detail="Vertex AI Search is disabled. Set VERTEX_AI_SEARCH_ENABLED=
            true and configure VERTEX_AI_PROJECT_ID, VERTEX_AI_ENGINE_
            ID, and an API key (VERTEX_AI_API_KEY or GOOGLE_CSE_API_KEY)."
  [google_kg_search]             state=not_configured  items=0
  [brave_web_search]             state=not_configured  items=0
```

## Tests run

- `pytest backend/app/tests` → **95 passed** (81 prior + 14 new):
  - 5 site-restricted tests (disabled-by-default, enabled-no-key,
    enabled+403 permission_denied, 410, 200 happy path)
  - 7 Vertex AI tests (disabled, missing project, missing engine,
    missing key, 403 → unavailable, 404 → not_configured, 429 →
    rate_limited, 200 normalized)
  - 1 listing test for Google alternatives
  - 1 ordering test
- Android: NOT rebuilt — no DTO/UI changes (existing enum already had
  `UNAVAILABLE` and `MANUAL_UI_REQUIRED` from prior commit; `type`
  is `String` on Android so new `web_search_ui_assisted` and
  `site_search` values deserialize without enum changes).

## Runtime result

**Honest, structured failure** with the path forward in plain text.
Web Search dispatch does not yet produce real evidence — but every
provider tells the operator exactly why and what to do next:
- Two Google JSON-API paths report `unavailable` with deprecation /
  denial detail.
- Vertex AI Search reports `not_configured` with the exact env vars
  to set; once configured, the provider is ready to issue searchLite
  queries against the discoveryengine endpoint and return real
  evidence.
- Brave reports `not_configured` (alternative non-Google fallback).

## Whether this produces real evidence now

**No, not yet.** Producing real evidence requires one of:
- (A) Operator runs the 4-step Vertex AI Search Console runbook in
  `docs/GOOGLE_SEARCH_PROVIDER_DECISION.md` (~10 min), or
- (B) Operator sets `BRAVE_SEARCH_API_KEY` (independent index, 1-step).

Either step is purely env-side work; the backend is ready for both.

## Files changed

```
backend/
  app/config.py                                          (+ Vertex AI + Site Restricted settings)
  app/providers/registry.py                              (web order + new providers)
  app/providers/web_search/google_cse_site_restricted.py (NEW — deprecated stub)
  app/providers/web_search/vertex_ai_search.py           (NEW — searchLite impl)
  app/services/provider_registry_service.py              (status rows)
  app/tests/conftest.py                                  (env neutralization)
  app/tests/test_google_cse_provider.py                  (order test updates)
  app/tests/test_google_site_restricted_and_vertex.py    (NEW — 12 tests)
  app/tests/test_providers_orchestrator.py               (listing + order + dispatch test updates)
.env.example                                             (+ 7 new env vars + docs)
docs/
  GOOGLE_SEARCH_PROVIDER_DECISION.md                     (NEW — research + runbook)
  FINAL_REPORT_GOOGLE_SITE_RESTRICTED_AND_VERTEX.md      (this file)
```

SearchAid deletions remain unstaged per project rule.
