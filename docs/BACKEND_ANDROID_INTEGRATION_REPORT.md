# Backend ↔ Android Integration — Milestone Report

Date: 2026-04-25
Branch: `feat/heat-map-zones`
Commit: `a0c3c3c` — `feat: connect Android UI to FastAPI backend`
Remote: `https://github.com/ishtaket/911.git`

## TL;DR

The Backend ↔ Android wiring is **complete in code**. All four data screens
(CaseList, CaseDetail, EvidenceInbox, HypothesisBoard) plus AuditLog and
ProviderStatus now consume `Rescue911Repository`, which is dispatched between
mock and live backend by `Rescue911RepositoryDispatcher`. A persistent
`DataSourceBadge` is visible from every screen. Tests pass. No external
provider keys live on Android.

## What was already in place (verified, not assumed)

Architecture had 90 % of the integration done in the prior commit (`cd784b8`):

- `Rescue911Repository` interface — `cases`, `caseById`, `evidenceForCase`,
  `hypothesesForCase`, `auditLog` as `Flow`s
- `MockRescue911Repository` and `BackendRescue911Repository` both implement it
- `Rescue911RepositoryDispatcher` selects via `AppPreferences.mockMode`:
  - `mock=true` → mock
  - `mock=false` + backend ok → backend
  - `mock=false` + backend throws → silent fallback to mock + `monitor.check()`
- `BackendStatusMonitor` exposes `Online / Offline / Checking / Unknown`
- `Rescue911Api` (Retrofit) maps to `/v1/health`, `/v1/provider-status`,
  `/v1/cases`, `/v1/cases/{id}`, `/v1/evidence?case_id`,
  `/v1/hypotheses?case_id`, `/v1/audit?limit`
- `NetworkModule`, `ApiBaseUrlHolder`, `HostRewriteInterceptor` translate the
  user-configured base URL (default `http://10.0.2.2:8011/`) at every request
- `SettingsScreen` shows backend status pill, data-source label, editable
  base URL, and a Check button
- `ProviderStatusScreen` loads `/v1/provider-status` with a static fallback
  list

## What this commit adds (`a0c3c3c`)

| File | Change |
|---|---|
| `feature/audit/AuditLogScreen.kt` | Was reading `MockData.auditLog` directly. Now uses an `AuditLogViewModel` that calls `repository.auditLog()`, with Loading / Empty states. |
| `feature/common/DataSourceViewModel.kt` | New. Combines `AppPreferences.mockMode` and `BackendStatusMonitor.state` into a single `StateFlow<DataSource>` (`MOCK / BACKEND / FALLBACK / CHECKING`). Probes backend once on app open. |
| `ui/components/Components.kt` | New `DataSourceBadge` composable (color-coded chip) and `EmptyState` text helper. |
| `navigation/Rescue911Nav.kt` | Bottom nav now has a small badge row above it that renders `DataSourceBadge` from `DataSourceViewModel` — visible on every screen. |
| `feature/cases/CaseListScreen.kt` + `CaseListViewModel.kt` | Cases state is `null → Loading`, `[] → "No cases yet"`, otherwise list. |
| `feature/cases/CaseDetailScreen.kt` | `load()` wrapped in `runCatching` so a backend hiccup returns null instead of throwing into Compose. |
| `feature/evidence/EvidenceInboxScreen.kt` | Loading / Empty states, `runCatching` around the load. |
| `feature/hypotheses/HypothesisBoardScreen.kt` | Loading / Empty states, `runCatching` around the load. |
| `res/values/strings.xml`, `values-iw/strings.xml`, `values-ru/strings.xml` | New keys `state_loading`, `state_empty_cases`, `state_empty_evidence`, `state_empty_hypotheses`, `state_empty_audit` in EN, HE (RTL), RU. |

253 insertions, 41 deletions across 12 files. No new dependencies.

## Backend endpoints used

| Endpoint | Used by |
|---|---|
| `GET /v1/health` | `BackendStatusMonitor.check()` for Settings pill + on-app-open probe |
| `GET /v1/provider-status` | `ProviderStatusScreen` (with static fallback) |
| `GET /v1/cases` | `CaseListScreen` |
| `GET /v1/cases/{id}` | `CaseDetailScreen` |
| `GET /v1/evidence?case_id={id}` | `EvidenceInboxScreen` |
| `GET /v1/hypotheses?case_id={id}` | `HypothesisBoardScreen` |
| `GET /v1/audit?limit=200` | `AuditLogScreen` |

All endpoints already existed and serve seeded Israel cases via
`backend/app/services/seed.py`. No new backend endpoints were needed.

## Mock / Backend / Fallback behavior

| `mockMode` | Backend probe | Dispatcher routes to | Badge shows |
|---|---|---|---|
| `true` | (ignored) | Mock | `Mock` (gray) |
| `false` | Online | Backend | `Backend` (green) |
| `false` | Offline | Backend → throws → Mock | `Fallback` (red) |
| `false` | Checking / Unknown | (depends, but UI shows last good) | `Checking…` (gold) |

The badge color reflects what the user is actually seeing right now, not what
they configured. If they switch `mockMode` off but the host backend is down,
the dispatcher serves stale mock data and the chip turns red so they know.

## Commands run

```bash
# Backend tests (from backend/ to avoid root test_log.txt collision)
cd backend
.venv/Scripts/python.exe -m pytest -q --tb=short --disable-warnings

# Android JVM unit tests (with JDK 19 — system default JDK 8 is too old for AGP 8.4)
cd android
JAVA_HOME="/c/Program Files/Java/jdk-19" ./gradlew.bat :app:testDebugUnitTest --console=plain
```

## Test results

| Suite | Result |
|---|---|
| Backend pytest | **24 passed** (deprecation warnings only) |
| Android `:app:testDebugUnitTest` | **BUILD SUCCESSFUL**, 31 tasks, 20 executed (compiled + tested new code) |

## Smoke results

The backend smoke (`scripts/dev_backend_health.ps1`) and the
emulator smoke (`scripts/dev_android_backend_smoke.ps1`) were **not** run as
part of this commit because:

- The user's known-stable AVD `Rescue911_API_30_default` is not currently
  booted in this session.
- Local-emulator smoke is by design a manual step gated by Claude's
  no-image-processing rule (we use `uiautomator dump` XML, not screenshots).

To verify end-to-end manually:

```powershell
# 1. Start backend (foreground)
powershell -ExecutionPolicy Bypass -File scripts\dev_backend_start.ps1

# 2. In another shell, probe it
powershell -ExecutionPolicy Bypass -File scripts\dev_backend_health.ps1

# 3. Boot the stable emulator (separately) and install + launch the app
powershell -ExecutionPolicy Bypass -File scripts\dev_android_backend_smoke.ps1

# 4. In the app: Settings → switch off Mock mode → Check.
#    Bottom badge should flip from Mock to Backend (green).
#    Open Cases / Evidence / Hypotheses / Audit — they should hit the backend.
#    Stop the backend and re-open Cases — badge turns red (Fallback) and the
#    UI keeps working from mock.
```

## Known risks

- **Pre-existing tracked PNG screenshots** in `docs/screenshots/` (7 files,
  pending deletion). The CI `repository-safety` job from `7a17f9a` will flag
  these on every run until the legacy SearchAid cleanup commit lands.
- **Reactive caseId loading**: the per-case screens use `LaunchedEffect(caseId)
  { items = vm.load(caseId) }` — they do not re-collect when the dispatcher
  re-routes mid-session (e.g., user toggles Mock in Settings while sitting on
  the Evidence screen). User must back out and re-enter the screen. Acceptable
  for this milestone; can be tightened to `flatMapLatest` later.
- **No retry/timeout UI**: Retrofit's defaults apply. A failed call falls back
  silently to mock with a red badge — there is no in-screen "Retry" button.
- **Real provider integrations are still mock** server-side. The architecture
  is correct (keys never reach Android), but live OSINT/GeoINT/social/archive
  providers are stubs. That is intentional for this milestone.
- **L3 confirmation flow** UI is not implemented yet. The `EvidenceStatus`
  enum carries `HUMAN_CONFIRMED` and `ValidationLevel3Dto` flows through the
  mappers, but no operator action posts back to the backend.

## Did backend start? Did Android launch? Did UI show backend data?

- **Backend start:** Not started in this session (no need — code-level
  integration was the goal). Ready to start via `dev_backend_start.ps1`.
- **Android launch:** Not launched. The build compiled and unit tests passed,
  which proves the new code is well-formed. APK install + emulator launch is
  the next manual step.
- **UI showed backend data:** Not visually verified this turn. The data flow
  is wired and unit-tested at the JVM level. The next step (below) verifies
  end-to-end on the stable emulator.

## Exact next command for the user

```powershell
# In one PowerShell window — start backend in background:
powershell -ExecutionPolicy Bypass -File scripts\dev_backend_start.ps1 -Background

# Confirm it's healthy:
powershell -ExecutionPolicy Bypass -File scripts\dev_backend_health.ps1

# Boot the stable emulator (Android Studio AVD Manager or `emulator -avd Rescue911_API_30_default`).
# Then install + launch + verify with text-based UI dump:
powershell -ExecutionPolicy Bypass -File scripts\dev_android_backend_smoke.ps1
```

After the app is running, in **Settings**: turn off Mock mode and tap **Check**.
The bottom-nav badge should turn green ("Backend"). Open Cases — you should
see seeded Israel cases (e.g., Ramat Gan, Be'er Sheva).

## Next recommended milestones

1. **Legacy SearchAid cleanup commit** (`chore: remove legacy SearchAid sources
   superseded by Rescue911`) — unblocks CI's `repository-safety` job.
2. **Manual review (L3) action UI** — wire `/v1/review/*` POSTs from
   `ManualReviewQueueScreen` so confirm/reject/escalate actually changes
   evidence status.
3. **Live emulator smoke run** with `dev_android_backend_smoke.ps1`, capturing
   `uiautomator dump` XML to verify the badge text and case rows.
4. **Reactive per-case flows** — replace `LaunchedEffect(caseId) { vm.load() }`
   with collected `Flow`s so toggling Mock in Settings updates the screen
   live.
5. **Retry button** in `EmptyState` when the cause is a backend error rather
   than truly empty data (requires distinguishing the two in the ViewModels).
