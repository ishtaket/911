# FINAL REPORT — Google web search prioritized

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`
**Backend:** 127.0.0.1:8011 (Android emulator: 10.0.2.2:8011)

## Google web API used

**Google Custom Search JSON API (Programmable Search)** — the only official Google web-search style endpoint.

- Endpoint: `https://www.googleapis.com/customsearch/v1`
- Auth: backend-side `GOOGLE_CSE_API_KEY` + `GOOGLE_CSE_ENGINE_ID` (cx). Both required.
- Confidence: `0.60`. `legal_basis: public`.
- Quote of constraint from operator brief: *"may be unavailable for new customers and may return 403."* Provider catches HTTP 403 explicitly and surfaces as `ProviderNotConfigured` with a clear "Custom Search JSON API may not be enabled / closed to new customers" message — never silent empty.

**Not used (with reason):**
- Google Knowledge Graph — entity lookup, not a web search engine. Kept as supplemental web-channel provider but display rule is unchanged.
- YouTube Data API — video / channel search; lives on the social channel.
- Google Vision — image analysis; lives in GeoINT.
- HTML scraping of `google.com/search?q=…` — **forbidden by project rules** (no scraping, no cookies, no fake identity).

## Required env vars

```
GOOGLE_CSE_API_KEY=<google-cloud-api-key-with-Custom-Search-API-enabled>
GOOGLE_CSE_ENGINE_ID=<programmable-search-engine-cx>
```

Both must be present for `state=connected`. If either is missing, provider raises `ProviderNotConfigured` at call time and `/v1/providers` reports `state=not_configured` at status time.

`.env.example` updated. Old approach of reusing `GOOGLE_MAPS_API_KEY` for CSE is removed.

## Provider order (registry, post-fix)

For the web channel, providers are queried in this order:

1. `google_cse`              — Google Custom Search JSON API (primary)
2. `google_kg_search`        — Google Knowledge Graph (entity lookup, supplemental)
3. `brave_web_search`        — Brave Search (alternative)
4. (`mock_web_search`)       — only when `MOCK_PROVIDERS=true`

Brave / KG code untouched, only ordered after CSE.

## /v1/providers (post-fix, MOCK_PROVIDERS=false, no CSE keys)

```json
{
  "provider_id": "google_cse",
  "type": "web_search",
  "display_name": "Google Custom Search JSON API",
  "state": "not_configured",
  "auth_type": "api_key",
  "configured": false,
  "requires_user_action": true,
  "safe_scope_description": "Official Google Programmable Search / Custom Search JSON API. Public web results only. Backend-side key only.",
  "note": "Set GOOGLE_CSE_API_KEY and GOOGLE_CSE_ENGINE_ID. The Custom Search JSON API may be unavailable for new Google Cloud projects — a 403 surfaces as not_configured."
}
```

## Direct backend response — fresh case

**Case:** `91c66d24-5edc-4b9f-8182-9ac40a2c1d04` ("Google Search Runtime Test")

`POST /v1/search/web/start/{case_id}` → HTTP 200

```
state=not_configured
items_returned=0  items_deduped=0
providers (in order):
  [google_cse]         state=not_configured  items=0  detail="GOOGLE_CSE_API_KEY is not set. Configure a Google Custom Search JSON API key in the backend env."
  [google_kg_search]   state=rate_limited    items=0  detail="Google KG quota / rate-limited (403)"
  [brave_web_search]   state=not_configured  items=0  detail="BRAVE_SEARCH_API_KEY is not set. Configure it in the backend env."
```

## Android visible banner

```
Dispatched web: state=not_configured, items_returned=0, items_stored=0, items_deduped=0. Providers: [google_cse=not_configured(0), google_kg_search=rate_limited(0), brave_web_search=not_configured(0)].
```

`google_cse` appears **first**, exactly as requested. No Android code changed — the structured-response wiring already in place displays providers in registry order.

## Behavior matrix (verified)

| State source | Provider raises | UI sees |
|---|---|---|
| Missing `GOOGLE_CSE_API_KEY` | `ProviderNotConfigured("GOOGLE_CSE_API_KEY is not set...")` | `google_cse=not_configured` |
| Missing `GOOGLE_CSE_ENGINE_ID` | `ProviderNotConfigured("GOOGLE_CSE_ENGINE_ID is not set...")` | `google_cse=not_configured` |
| HTTP 403 from API | `ProviderNotConfigured("Google Custom Search API returned 403...")` | `google_cse=not_configured` with detail explaining project-access issue |
| HTTP 429 from API | `RateLimitError("Google Custom Search rate-limited (429)...")` | `google_cse=rate_limited` |
| HTTP 200 with items | normalized → `ProviderResult` (provider=google_cse, source_type=WEB) | `google_cse=ok(N)` and rendered as evidence |

The 403 path is verified by unit test (`test_cse_403_raises_not_configured_with_clear_message`); the live path is verified by runtime probe above.

## Tests run

- `pytest backend/app/tests` → **78 passed** (69 from prior commit + 9 new in `test_google_cse_provider.py`).
- New tests cover: missing api_key, missing cse_id, 200-normalized response, 403 → ProviderNotConfigured with clear message, 429 → RateLimitError, registry ordering (`google_cse` first with and without keys), `/v1/providers` exposes new display_name, no secrets leaked.
- Android: **NOT rebuilt** per task rule "run only if Android code changed" — Android code untouched; the ordered banner is rendered by the existing structured-response code.

## Files changed

```
backend/.env  — NOT touched (operator decides keys)
.env.example                                              (added 2 keys + comment)
backend/app/config.py                                     (added 2 settings fields)
backend/app/providers/web_search/google_cse.py            (real API impl)
backend/app/providers/registry.py                         (re-ordered web providers)
backend/app/services/provider_registry_service.py         (display_name + key check)
backend/app/api/routes_provider_status.py                 (display_name + key check)
backend/app/tests/conftest.py                             (neutralize CSE env)
backend/app/tests/test_google_cse_provider.py             (new — 9 tests)
backend/app/tests/test_providers_orchestrator.py          (override CSE keys in strict test)
docs/FINAL_REPORT_GOOGLE_CSE_PRIORITY.md                  (this file)
```

SearchAid deletions remain unstaged per project rule.
