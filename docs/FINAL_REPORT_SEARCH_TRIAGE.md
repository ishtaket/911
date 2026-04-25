# FINAL REPORT — Search Failure Triage

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`
**Backend:** 127.0.0.1:8011 (Android emulator: 10.0.2.2:8011)

## Test cases

| Phase | case_id | Title |
|---|---|---|
| Diagnosis | `428fb91b-7a09-4caa-8005-f1b20e4d6b92` | Search Triage Test |
| Acceptance | `d6746b7d-7617-4f10-8d0c-c88cdbf73940` | Search Triage Test 3 |

## Provider states from `/v1/providers` (post-fix, MOCK_PROVIDERS=false)

```
brave_web_search        = not_configured
google_cse              = not_configured
google_kg_search        = connected   (real API; 403 quota at runtime → rate_limited)
serpapi                 = not_configured
youtube_data_api        = connected   (real API; 403 quota at runtime → rate_limited)
telegram_public         = not_configured
reddit_public           = auth_required
meta_public_pages       = auth_required
x_public                = auth_required
vk_public               = auth_required
tiktok_public           = auth_required
instagram_public        = auth_required
linkedin_public         = auth_required
wayback_availability    = connected
wayback_cdx             = connected
common_crawl            = connected
exif_reader             = connected
google_vision           = connected
osm_nominatim           = connected
```

## Root causes identified

1. **Web/Social returned bare `list[Evidence]`** — no state, no diagnostics. When all real providers raised `ProviderNotConfigured` / `ProviderAuthRequired`, the dispatch returned `[]` and the operator screen showed a silent empty state.
2. **`GoogleCseWebSearchProvider` silently fell back to `MockWebSearchProvider`** even with `MOCK_PROVIDERS=false`. Strict-mode rule violation. Combined with content-hash dedupe in `evidence_service`, this produced the operator-visible behavior: first call returned mock items, second call returned `[]` (silent dedupe), so search appeared broken.
3. **8 social stub providers** (facebook, instagram, linkedin, reddit, telegram, tiktok, twitter_x, vk) all silently fell back to `MockSocialSearchProvider` regardless of `MOCK_PROVIDERS`. Same strict-mode violation.

## Fix summary

### Backend
- **New `WebSocialStartResponse`** Pydantic model in `app/services/search_orchestrator.py` with: `state`, `case_id`, `channel`, `providers_attempted`, `providers_with_items`, `items_returned`, `items_deduped`, `message`, `providers: list[ProviderRunInfo]`, `evidence`. State is one of: `completed`, `mock`, `deduplicated`, `no_results`, `not_configured`, `auth_required`, `rate_limited`, `provider_error`.
- **`run_web` / `run_social` rewritten** to track per-provider outcomes (catching `ProviderNotConfigured`, `ProviderAuthRequired`, `RateLimitError`, generic exceptions) and surface per-provider state + detail.
- **`evidence_service.normalize_and_store_with_stats`** added so dispatch knows how many results were filtered as duplicates.
- **`routes_search.py`** updated `web/start` and `social/start` `response_model` to `WebSocialStartResponse`.
- **Strict mode enforced** in `google_cse.py` and 8 social stubs — they now raise `ProviderNotConfigured` / `ProviderAuthRequired` instead of silently mocking.

### Android
- **`WebSocialStartResponseDto` + `ProviderRunInfoDto`** in `data/remote/dto/Dtos.kt`.
- **`Rescue911Api`** `startWebSearch` / `startSocialSearch` now return `WebSocialStartResponseDto`.
- **`EvidenceInboxScreen.dispatchSearch`** consumes the structured response and renders a banner with `state`, items_returned, items_stored, items_deduped, and per-provider summary.

## Direct backend response summary (post-fix, fresh case d6746b7d…)

| Endpoint | HTTP | state | Detail |
|---|---|---|---|
| `POST /v1/search/web/start/{cid}` | 200 | `not_configured` | brave=not_configured(0), google_cse=not_configured(0), google_kg_search=rate_limited(0) |
| `POST /v1/search/social/start/{cid}` | 200 | `auth_required` | 7×auth_required, 1×rate_limited(youtube), 1×not_configured(telegram) |
| `POST /v1/search/archive/start/{cid}` | 200 | `no_targets` | targets_attempted=0 (no URL evidence yet) |
| `POST /v1/geoint/start/{cid}` | 200 | `no_media_uploaded` | results=[] |

## Android visible banner text (uiautomator dump on emulator-5554)

- **Web:** `Dispatched web: state=not_configured, items_returned=0, items_stored=0, items_deduped=0. Providers: [brave_web_search=not_configured(0), google_cse=not_configured(0), google_kg_search=rate_limited(0)].`
- **Social:** `Dispatched social: state=auth_required, items_returned=0, items_stored=0, items_deduped=0. Providers: [facebook_public=auth_required(0), instagram_public=auth_required(0), tiktok_public=auth_required(0), youtube_data_api=rate_limited(0), telegram_public=not_configured(0), reddit_public=auth_required(0)] +3 more.`
- **Archive:** `No URLs in this case yet. Run a web or social search first; archive providers will then have public URL anchors to query. (state=no_targets, targets_attempted=0)`
- **GeoINT:** `GeoINT requires uploaded media. Use POST /v1/media (not yet wired) to upload an image, then re-dispatch. (state=no_media_uploaded)`
- **DataSourceBadge:** `Data: Backend` (unchanged)

## Files changed

```
backend/app/api/routes_search.py
backend/app/providers/social/facebook.py
backend/app/providers/social/instagram.py
backend/app/providers/social/linkedin.py
backend/app/providers/social/reddit.py
backend/app/providers/social/telegram.py
backend/app/providers/social/tiktok.py
backend/app/providers/social/twitter_x.py
backend/app/providers/social/vk.py
backend/app/providers/web_search/google_cse.py
backend/app/services/evidence_service.py
backend/app/services/search_orchestrator.py
backend/app/tests/test_providers_orchestrator.py
android/app/src/main/java/com/rescue911/osint/data/remote/Rescue911Api.kt
android/app/src/main/java/com/rescue911/osint/data/remote/dto/Dtos.kt
android/app/src/main/java/com/rescue911/osint/feature/evidence/EvidenceInboxScreen.kt
docs/FINAL_REPORT_SEARCH_TRIAGE.md   (this file)
```

SearchAid deletion (~260 files in `git status` as ` D`) is **unstaged and untouched** per task scope.

## Tests run

- **Backend:** `python -m pytest backend\app\tests` → **69 passed** (incl. new `test_web_dispatch_state_not_configured_when_strict_no_keys`).
- **Android:** `gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL** (3 unit-test files, JDK 19).
- **Android:** `gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (APK installed via `adb install -r`).

## Acceptance criteria

| Check | Result |
|---|---|
| Web: visible result/state after dispatch | ✅ `state=not_configured` banner |
| Social: visible result/state after dispatch | ✅ `state=auth_required` banner |
| Archive: visible state after dispatch | ✅ `state=no_targets` banner |
| GeoINT: visible state after probe | ✅ `state=no_media_uploaded` banner |
| No silent empty screen | ✅ |
| No crash/ANR/fatal | ✅ (PID 6184 stable) |
| Backend log shows all 4 POST requests | ✅ (web/social/archive/geoint each logged twice — direct probe + Android tap) |
| Android DataSourceBadge remains Backend | ✅ |
