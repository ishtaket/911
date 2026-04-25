# Testing

Three-layer strategy applies to *both* backend and Android.

## Backend (`backend/app/tests/`)

- **L1 — Unit:** schema invariants, validation engine (`test_schemas.py`, `test_validation.py`).
- **L2 — Mocked-provider integration:** every mock provider returns well-formed `ProviderResult` (`test_providers_mock.py`); query builder produces multilingual variants (`test_query_builder.py`).
- **L3 — Smoke / E2E:** full mocked flow `case → query plan → search → geoint → promote → review → audit` (`test_api_flow.py`).

Run with:

```powershell
.\scripts\dev_backend_test.ps1
```

Current status: **19 tests passing**.

## Android (`android/app/src/test/` + `androidTest/`)

- **L1 — JVM unit:** `ValidationTest`, `MockDataTest`.
- **L2 — Compose UI:** `ComponentsUiTest` checks `ValidationBadge` (Human Confirmed) and `RiskChip` (CRITICAL) render.
- **L3 — ADB smoke:** `scripts/dev_android_smoke.ps1` builds debug, installs, launches `MainActivity`, scans logcat for `FATAL EXCEPTION`.

## Three-level *evidence* validation tested

L1 (automated) + L2 (cross-source) + L3 (human) are tested in `test_validation.py` and `test_api_flow.py`. The invariant **"no Confirmed without L3"** is asserted directly.

## Fixtures

- Hebrew + Russian + English name variants in `MockData` and `test_query_builder.py`.
- Israel bbox sanity test ensures GeoINT mock candidates fall within Israel.
- Provider failure path: when a real-API provider fails, fallback to mock is exercised by `test_providers_mock.py` indirectly (mock fallback paths share signatures).

## CI

`.github/workflows/ci.yml`:
- backend pytest on every push/PR.
- Android lint / config validation; full Gradle build deferred until SDK is provisioned in CI.
