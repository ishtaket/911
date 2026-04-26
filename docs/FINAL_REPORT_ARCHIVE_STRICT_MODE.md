# FINAL REPORT — Archive strict mode (no fake evidence in Backend mode)

**Date:** 2026-04-26
**Branch:** `feat/heat-map-zones`
**Backend:** 127.0.0.1:8011

## Mock-archive leakage source

`backend/app/providers/archive/snippet.py` (`SearchSnippetArchiveProvider`)
silently fell back to `MockArchiveProvider` regardless of
`MOCK_PROVIDERS=true/false`. `MockArchiveProvider` produces
deterministic items titled
`"[mock archive] snapshot N for: <url>"` with confidence 0.25.
That title was the exact string the operator saw on Archive Results
in Backend mode.

The same pattern existed in `backend/app/providers/archive/mirrors.py`
(`PublicMirrorArchiveProvider`). It was not in the active registry,
but the silent fallback would re-introduce the bug if the provider
were ever re-added.

## How it was gated

Three layers, defense in depth:

1. **Stub providers raise instead of mocking.**
   `SearchSnippetArchiveProvider.lookup()` and
   `PublicMirrorArchiveProvider.lookup()` now raise
   `ProviderNotConfigured` with a clear "no real implementation yet,
   use Vertex AI Search or wait for real impl" message. They no
   longer reach `MockArchiveProvider` at all.
2. **Orchestrator distinguishes the new error.**
   `search_orchestrator.run_archive` now catches
   `ProviderNotConfigured` and `RateLimitError` separately from the
   generic `Exception` path, audit-logging
   `provider_not_configured` / `rate_limited` actions instead of
   the lossy `provider_error`.
3. **Evidence-service strict-mode guard.**
   `evidence_service.normalize_and_store_with_stats` now drops any
   `ProviderResult` whose `provider` name starts with `mock_`
   when `MOCK_PROVIDERS=false`. This is a belt-and-suspenders
   defense: the registry already gates `MockArchiveProvider` behind
   the flag, but if a future regression silently mocks again, the
   evidence row never enters the store in real mode.

The registry itself was already correct: `get_archive_providers`
includes `MockArchiveProvider` only when `s.mock_providers` is true.
No registry change was needed.

## Tests added (`test_archive_strict_mode.py`)

7 new tests, all pass:
- `archive_registry_excludes_mock_in_strict_mode`
- `archive_registry_includes_mock_in_dev_mode`
- `snippet_provider_raises_not_configured`
- `mirrors_provider_raises_not_configured`
- `evidence_service_drops_mock_named_provider_in_strict_mode`
- `evidence_service_keeps_mock_named_provider_in_dev_mode`
- `wayback_real_capture_normalized` (regression guard)

Full backend suite: **102 passed**, 0 failed.
Android: `:app:testDebugUnitTest` BUILD SUCCESSFUL.

## Runtime proof (case `8b7fd57f-234c-4641-9df7-338bb6af1587` —
"Archive Strict Mode Runtime Test", `Dima la`, `Ashkelon`)

### Backend dispatch (post-strict)

```
POST /v1/search/web/start  ->  HTTP 200
  state=completed  items_returned=32  items_stored=5
  vertex_ai_search=ok(32) — only real Google-backed evidence

POST /v1/search/archive/start  ->  HTTP 200
  state=completed  targets_attempted=9  evidence=10
```

### Stored evidence — `/v1/evidence?case_id=…`

```
provider                  count
vertex_ai_search          5
wayback_cdx               25     <- second dispatch added more
wayback_availability      1
mock_archive              0   <-- ZERO. Strict mode confirmed.
search_snippet            0   <-- raises ProviderNotConfigured
```

### Audit trail (new actions)

```
provider_not_configured   target_type=archive   provider=search_snippet
provider_call             target_type=archive   provider=wayback_cdx
provider_call             target_type=archive   provider=wayback_availability
…
```

`provider_not_configured` is the new audit action introduced by the
discriminated `except` in `run_archive`.

### Android UI (uiautomator dump on emulator-5554)

```
Archive Results — Wayback / Common Crawl
Backend queries public archives (Wayback CDX, Common Crawl, public mirrors). No paywalled data.
Dispatch archive search now
Wayback snapshot
Candidate
L1 L2 L3
timestamp=19981212013921 status=200 mime=text/html original=http://facebook.com:80/
Next action: Verify against live source if reachable
Wayback snapshot
…
```

- `[mock archive]` snapshot cards visible: **0**
- `Wayback snapshot` cards visible: 3+ (more below the fold; case has
  26 archive evidence rows in total)
- DataSourceBadge: **`Data: Backend`**
- No crash / ANR

## Acceptance — all met

- ✅ Mock archive provider registered ONLY when `MOCK_PROVIDERS=true`
- ✅ Strict backend mode lists only real public-archive providers
  (`wayback_availability`, `wayback_cdx`, `common_crawl`, plus the
  now-honest `search_snippet` stub)
- ✅ `SearchSnippetArchiveProvider` no longer leaks mock evidence
- ✅ Mock-named providers cannot be stored in strict mode (guarded
  in `evidence_service`)
- ✅ Real Wayback captures continue to flow through and are stored
  with `provider=wayback_cdx` / `wayback_availability`,
  `source_type=archive`, real `timestamp/status/mime` metadata
- ✅ Archive dispatch in Android shows real Wayback cards only;
  no `[mock archive]` text anywhere in the operator UI

## Files changed

```
backend/app/providers/archive/snippet.py            (raise ProviderNotConfigured)
backend/app/providers/archive/mirrors.py            (raise ProviderNotConfigured)
backend/app/services/search_orchestrator.py         (discriminated except in run_archive)
backend/app/services/evidence_service.py            (strict-mode mock_*-guard)
backend/app/tests/test_archive_strict_mode.py       (NEW — 7 tests)
docs/FINAL_REPORT_ARCHIVE_STRICT_MODE.md            (this file)
```

No `.env`, secrets, logs, APKs, build outputs, `.venv`, or
`android/local.properties` committed. SearchAid deletions remain
unstaged per project rule. Vertex AI Search, Google CSE, Brave,
Wayback, Common Crawl, and Android case-creation untouched.
