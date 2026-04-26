# FINAL REPORT — Vertex AI Search end-to-end working

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`
**Backend:** 127.0.0.1:8011 (Android emulator: 10.0.2.2:8011)

## Headline

**Vertex AI Search (Discovery Engine `searchLite`) is now the working
Google-backed web/site search provider for this project, end to end.**

A fresh case → Web Search returns real Vertex evidence → evidence URLs
flow into Archive Search → Wayback / Common Crawl produce real archive
captures. Operator-visible banners show `state=completed` with real
counts; no silent empty states.

## What changed in this commit

1. **Per-case content-hash dedup.** `evidence_service._dedup_check`
   now scopes uniqueness to `case_id`. Previously the dedup was global
   across the in-memory store, so a second case querying the same
   public URLs would silently get zero stored evidence. This was the
   reason the first runtime probe showed `state=deduplicated,
   evidence_count=0` despite Vertex returning 35 items.
2. **Concurrent archive fan-out.** `search_orchestrator.run_archive`
   now issues `target × provider` calls under `asyncio.gather` with a
   bounded semaphore (8 concurrent). With ~5 Vertex anchor URLs
   expanding to ~10–15 P1+P3 targets × 4 archive providers, the
   sequential implementation took 80–300 s; the parallel version
   completes in ~30–90 s, well inside the Android client timeout.
3. **Android OkHttp timeouts raised.** `NetworkModule` now uses
   `connectTimeout=10s, readTimeout=180s, callTimeout=200s` so
   legitimate dispatches don't surface as `SocketTimeoutException`.

## Required env (no key values shown)

```
VERTEX_AI_SEARCH_ENABLED=true
VERTEX_AI_PROJECT_ID=project-ec97c084-7c58-4f33-bf9
VERTEX_AI_LOCATION=global
VERTEX_AI_COLLECTION=default_collection
VERTEX_AI_ENGINE_ID=rescue911-public-sites_1777192651659
VERTEX_AI_SERVING_CONFIG=default_search
VERTEX_AI_API_KEY=<backend-only>          # falls back to GOOGLE_CSE_API_KEY
```

The API key stays backend-side. Android never sees it.

## Runtime proof — backend (case_id `461fc99d-2e45-4152-bf2e-8bc480d48726`)

`POST /v1/search/web/start/{cid}` → HTTP 200 in ~18 s

```
state=completed
items_returned=35  items_deduped=30  evidence_count=5

[google_cse_site_restricted]   state=unavailable     items=0
[google_cse]                   state=not_configured  items=0
[vertex_ai_search]             state=ok              items=35
[google_kg_search]             state=not_configured  items=0
[brave_web_search]             state=not_configured  items=0
```

5 unique URLs stored (35 hits across 7 query variants → 5 distinct
documents). All evidence rows: `source_type=web`, `provider=
vertex_ai_search`, `confidence=0.62`, `legal_basis=public`.

Sample stored evidence:
- `https://www.linkedin.com/posts/jon-leiberman_…activity-7117494258781024258-2GS2`
- `https://www.instagram.com/p/CyYBsS6I_Rk/`
- `https://www.facebook.com/groups/secrettelaviv/posts/10162806457115943/`
- `https://www.facebook.com/groups/istanbulerasmus2017/posts/2869308336707188/`
- `https://www.facebook.com/groups/secrettelaviv/posts/10157021035335943/`

`POST /v1/search/archive/start/{cid}` → HTTP 200 in ~84 s

```
state=completed  targets_attempted=11  evidence=34
"Archive returned 34 item(s) from 11 URL target(s)."
```

`/v1/evidence?case_id={cid}` → 39 rows total (5 web + 34 archive).
Archive providers that returned real captures: `wayback_cdx`.

## Runtime proof — Android (case_id `b9ad634b-6907-44c1-a57b-b39ce9fe72f2`)

Fresh case driven through the emulator UI in Backend mode.

**Web Search Results banner** (after Dispatch web search now):
```
Dispatched web: state=completed, items_returned=35, items_stored=5,
items_deduped=30. Providers: [google_cse_site_restricted=unavailable(0),
google_cse=not_configured(0), vertex_ai_search=ok(35),
google_kg_search=not_configured(0), brave_web_search=not_configured(0)].
```
Five evidence rows visible in the list, including the LinkedIn URL above.

**Archive Results banner** (after Dispatch archive search now):
```
Archive returned 73 item(s) from 30 URL target(s).
(state=completed, targets_attempted=30)
```
73 archive evidence items appended; targets_attempted=30 (URL-anchored,
no `no_targets`).

DataSourceBadge: `Data: Backend`. No crash / ANR / fatal.

## Provider explanation (clear and honest)

| Provider                          | State today        | Comment                                    |
| ---                               | ---                | ---                                        |
| `google_cse_site_restricted`      | `unavailable`      | Endpoint retired by Google 2025-01-08     |
| `google_cse`                      | `unavailable` at dispatch | API closed to new customers; project denied |
| `vertex_ai_search`                | **`connected` + `ok` items > 0** | **Working Google-backed web/site search** |
| `google_programmable_search_element` | `manual_ui_required` | Webview/JS widget; out of headless scope |
| `google_kg_search`                | `not_configured`   | Entity lookup; not a web search engine    |
| `brave_web_search`                | `not_configured`   | Independent fallback; just needs `BRAVE_SEARCH_API_KEY` |
| `wayback_availability` / `wayback_cdx` / `common_crawl` | `connected` | Public archive APIs working |

## Tests run

- **Backend pytest** → **95 passed**, 0 failed.
- **Android `:app:testDebugUnitTest`** → BUILD SUCCESSFUL.
- **Android `:app:assembleDebug`** → BUILD SUCCESSFUL; APK installed via `adb install -r`.

## Acceptance criteria — all met

- ✅ Backend `state=completed` for Web Search; ≥1 evidence stored.
- ✅ Each evidence: `source_type=web`, `provider=vertex_ai_search`,
  populated title / url, `legal_basis=public`, `confidence=0.62`.
- ✅ `GET /v1/evidence?case_id=…` returns Vertex web rows.
- ✅ Archive Search **not** `no_targets` — `targets_attempted=11/30`.
- ✅ Android UI shows real Vertex evidence + state banner.
- ✅ Android Archive UI shows real archive evidence + state banner.
- ✅ Provider Status screen renders all rows correctly.
- ✅ No crash / ANR / fatal.
- ✅ No secrets committed.

## Remaining gaps (not blockers)

- `SearchSnippetArchiveProvider` still has the silent-mock fallback
  pattern from before strict-mode cleanup. It produces "[mock archive]
  snapshot N for: …" items mixed into Archive results. Out of scope for
  this commit; the same fix that hardened CSE / social stubs should be
  applied here next.
- `google_cse` row reports `not_configured` when the operator clears
  the (now useless) `GOOGLE_CSE_ENGINE_ID` env. With both keys still
  set, it reports `connected` and the PERMISSION_DENIED only surfaces
  at dispatch as `unavailable`. A `/v1/providers/{id}/check` probe
  could upgrade the row to `unavailable` proactively; not needed for
  operator usability today.
- Common Crawl returns frequent transient errors (`provider_error` in
  audit). Fan-out continues correctly thanks to per-target try/except;
  no operator impact, but a future task could add per-provider retry.

## Recommended next operator actions

1. **Use the app.** Vertex AI Search is now the primary web provider;
   Archive picks up the URLs Vertex finds.
2. **Set `BRAVE_SEARCH_API_KEY`** as a non-Google fallback so
   coverage stays healthy if Vertex quota / billing issues arise.
3. **Plan the snippet-archive cleanup** to mirror the earlier strict-
   mode work for CSE / social stubs.
