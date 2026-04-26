# FINAL REPORT — Google CSE `unavailable` + Google web alternatives

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`

## Why OAuth does NOT fix Google CSE 403 PERMISSION_DENIED

The runtime error reported by the operator was:

```
HTTP 403 PERMISSION_DENIED
"This project does not have the access to Custom Search JSON API."
```

This is **project-level account denial** by Google — distinct from
"missing key" or "OAuth required". Three independent reasons OAuth
cannot fix it:

1. **Custom Search JSON API only accepts API-key auth.** The endpoint
   `https://www.googleapis.com/customsearch/v1` does not have an OAuth
   path documented for general web search. Adding an OAuth flow
   produces nothing the API will honour.
2. **The denial is at the Google Cloud project level**, not the user
   level. OAuth changes the *user* identity that calls the API; it does
   not grant the *project* permission to enable the API. A signed-in
   user from a project that lacks API access still gets PERMISSION_DENIED.
3. **Google has closed Custom Search JSON API to many new projects.**
   Even if OAuth were accepted, an operator request to enable the API
   in Google Cloud Console for a new project commonly fails. This is a
   business/policy gate, not a code gate.

Therefore the project explicitly does **not** add an OAuth path for
Custom Search JSON API. The 403 PERMISSION_DENIED is now mapped to a
new state, `unavailable`, distinct from `not_configured`.

## State machine extension (backend + Android)

`ProviderState` (backend) and `ProviderStateDto` (Android) now include:

| State | Meaning | Operator action |
|---|---|---|
| `unavailable` | Upstream API denies this account/project at the org level. | Request access from upstream OR pick a different provider. **OAuth will not fix this.** |
| `manual_ui_required` | Provider depends on a browser-assisted UI flow; cannot be invoked headlessly. | Use the dedicated UI in the app (deferred work). |

`ProviderType` extended with:

| Type | Meaning |
|---|---|
| `web_search_ui_assisted` | Webview/iframe-based public search element. |
| `site_search` | Indexed allow-list site search (e.g., Vertex AI Search). |

## Google alternatives now visible in `/v1/providers`

```text
provider_id=google_cse                              type=web_search                 state=connected (until dispatch reveals 403=unavailable)
provider_id=google_programmable_search_element      type=web_search_ui_assisted     state=manual_ui_required (forced)
provider_id=vertex_ai_search                        type=site_search                state=not_configured (service account + data store TBD)
provider_id=google_kg_search                        type=web_search                 state=connected/rate_limited (entity lookup, NOT web search)
provider_id=youtube_data_api                        type=youtube                    state=connected/rate_limited (social channel)
```

Detailed alternatives doc: `docs/GOOGLE_WEB_SEARCH_ALTERNATIVES.md`.

## Recommended provider for real web search **now**

**Brave Search Web API.**

- Independent index (not a Google reseller), so no PERMISSION_DENIED gate.
- Backend already has a working `BraveWebSearchProvider` with strict
  mode (no silent mock fallback).
- Single-line operator action:

  ```
  BRAVE_SEARCH_API_KEY=<your-brave-key>     # in backend/.env
  ```

  Restart backend; `state=connected` for `brave_web_search`; Web Search
  dispatch produces real evidence (`provider=brave_web_search`,
  `source_type=web`, `legal_basis=public`).

- Backend-only key. Never ships to Android.

Provider order remains `google_cse → google_kg_search → brave_web_search`.
With Brave configured, Web dispatch will produce `state=completed` from
Brave items while still surfacing `google_cse=unavailable` in the
per-provider list — operator sees the truth without losing real results.

## Direct backend response — fresh case (real 403 from Google)

**Case:** `58e63fde-683f-4075-8d45-1120c30a09dc` ("CSE Unavailable Runtime Test")

`POST /v1/search/web/start/{cid}` → HTTP 200

```
state=not_configured
items_returned=0  items_deduped=0
providers (in order):
  [google_cse]        state=unavailable    items=0  detail="Google Custom Search JSON API is unavailable for this Google Cloud project. OAuth will not fix this — Custom Search JSON API only accepts API-key auth and the denial is at the project / account level. Either request access from Google or use a different web-search provider."
  [google_kg_search]  state=not_configured items=0  detail="GOOGLE_KG_API_KEY is not set..."
  [brave_web_search]  state=not_configured items=0  detail="BRAVE_SEARCH_API_KEY is not set..."
```

Audit log records `provider_unavailable` action for `google_cse` (new).

## Android visible UI

**Web inbox banner (after dispatch):**

```
Dispatched web: state=not_configured, items_returned=0, items_stored=0,
items_deduped=0. Providers: [google_cse=unavailable(0),
google_kg_search=not_configured(0), brave_web_search=not_configured(0)].
```

**Provider status screen rows:**

- `Brave Search — Not configured`
- `Google Custom Search JSON API — Connected`
  *(connected here is the optimistic registry status — keys are set;
  the actual project-level denial only manifests at dispatch as
  `unavailable`. A future `/v1/providers/{id}/check` probe will upgrade
  the status row to `unavailable` proactively.)*
- `Google Programmable Search Element (UI-assisted) — Manual UI required`
- `Vertex AI Search (site search) — Not configured`

## Tests run

- `pytest backend/app/tests` → **81 passed** (78 prior + 3 new):
  - `test_cse_403_permission_denied_raises_unavailable`
  - `test_provider_listing_includes_google_alternatives`
  - `test_web_dispatch_state_unavailable_when_cse_returns_permission_denied`
- Android: `gradlew :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**.
- APK installed via `adb install -r`; runtime UI verified.

## Files changed

```
backend/
  app/providers/base.py                       (ProviderUnavailable exception)
  app/providers/web_search/google_cse.py      (PERMISSION_DENIED detection)
  app/schemas/provider_status.py              (UNAVAILABLE/MANUAL_UI_REQUIRED states + new types)
  app/services/provider_registry_service.py   (forced_state arg + 2 new rows)
  app/services/search_orchestrator.py         (catch ProviderUnavailable + hint)
  app/tests/test_google_cse_provider.py       (split 403 generic vs PERMISSION_DENIED)
  app/tests/test_providers_orchestrator.py    (new state-list + alternatives + unavailable e2e)
android/
  app/src/main/java/com/rescue911/osint/data/remote/dto/Dtos.kt              (UNAVAILABLE + MANUAL_UI_REQUIRED)
  app/src/main/java/com/rescue911/osint/feature/providers/ProviderStatusScreen.kt (handle new states)
  app/src/main/res/values/strings.xml         (2 new state labels)
docs/
  GOOGLE_WEB_SEARCH_ALTERNATIVES.md           (design notes, recommendation)
  FINAL_REPORT_GOOGLE_CSE_403_UNAVAILABLE.md  (this file)
```

## What we explicitly did NOT do

- No OAuth flow for Custom Search JSON API.
- No HTML scraping of `google.com/search?q=…`.
- No cookies, no signed-in mimicry.
- No Google credentials shipped to Android.
- No silent mock fallback for any web provider.
- No SearchAid file changes (deletions remain unstaged per project rule).
