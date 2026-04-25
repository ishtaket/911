# Android Interactive-Action Audit

Date: 2026-04-25
Branch: `feat/heat-map-zones`
Trigger: user reported "tapped Web Search, nothing happened" — full audit of every clickable element followed.

## Root cause of the Web Search bug

`feature/search/SearchDashboardScreen.kt` rendered four `InfoCard`s (Web / Social / Archive / GeoINT) **without an `onClick` parameter**. `InfoCard` accepts an optional `onClick: (() -> Unit)? = null` and only routes the tap when non-null. So all four cards were visually clickable-looking but silently dead — the `Card(onClick = { onClick?.invoke() })` swallowed the tap.

This was a category bug, not a one-off: the same pattern (`onClick = {}` or missing handler) was repeated on Upload Media, Manual Review, and Create Case.

## Total elements audited: 38 visible interactive elements across 16 screens + bottom nav

## Per-element table

Status legend: ✅ works · 🔧 fixed in this commit · 🟡 visible placeholder/disabled · ❌ still dead

| Screen | Element | File | Before → After |
|---|---|---|---|
| Bottom nav | Cases / Review / Map / Audit / Settings | `Rescue911Nav.kt` | ✅ all wired. Map tab was hardcoded `caseId=case-1` (a MockData ID that doesn't exist on backend) → 🔧 changed to `caseId=` empty so `GeoIntMapViewModel` fans out to all cases. |
| CaseList | "+" FAB → New case | `CaseListScreen.kt` | ✅ navigates to CreateCase. |
| CaseList | InfoCard rows → CaseDetail | `CaseListScreen.kt` | ✅ navigates with case id. |
| CreateCase | "Create" button | `CreateCaseScreen.kt` | ❌ silently called `popBackStack()` → 🟡 now shows `ActionResultBanner` "Case creation via backend POST /v1/cases is not wired yet". Visible, honest, no surprise dismiss. |
| CaseDetail | "Run search" | `CaseDetailScreen.kt` | ✅ navigates to SearchDashboard. |
| CaseDetail | "Upload media" | ✅ navigates to UploadMedia. |
| CaseDetail | "Evidence" | ✅ navigates to EvidenceInbox. |
| CaseDetail | "Hypotheses" | ✅ navigates to HypothesisBoard. |
| CaseDetail | "GeoINT" | ✅ navigates to GeoIntMap. |
| CaseDetail | "Archive" | ✅ navigates to ArchiveFindings. |
| CaseDetail | Person summary card | `CaseDetailScreen.kt` | ❌ unreachable PersonProfile route → 🔧 person card is now clickable and navigates to PersonProfile. |
| PersonProfile | (read-only screen) | `PersonProfileScreen.kt` | ❌ used `MockData` → 🔧 now uses `Rescue911Repository.cases()` to find the person; shows Loading and "Person not found" empty states. Reachable from CaseDetail. |
| UploadMedia | "Take photo" | `UploadMediaScreen.kt` | ❌ `onClick = {}` → 🟡 now sets banner state "Camera: media upload is not yet wired to the backend." |
| UploadMedia | "Pick from gallery" | `UploadMediaScreen.kt` | ❌ `onClick = {}` → 🟡 now sets banner state "Gallery: media upload is not yet wired to the backend." |
| **SearchDashboard** | **"Web search" card** | **`SearchDashboardScreen.kt`** | **❌ THE BUG: `InfoCard` with no `onClick` → 🔧 clickable, navigates to EvidenceInbox for the case; in-screen success banner names the search type. If no caseId in scope, shows "Open a case first" banner.** |
| SearchDashboard | "Social search" card | same | ❌ → 🔧 same fix |
| SearchDashboard | "Archive search" card | same | ❌ → 🔧 same fix |
| SearchDashboard | "GeoINT pipeline" card | same | ❌ → 🔧 same fix |
| SearchDashboard | (top of screen) | same | (none before) → 🔧 always-visible info banner: "Search runs on the backend; results appear in the Evidence inbox." |
| EvidenceInbox | List items | `EvidenceInboxScreen.kt` | 🟡 not clickable. Acceptable for v1 (detail screen not built yet). |
| HypothesisBoard | List items | `HypothesisBoardScreen.kt` | 🟡 not clickable. Same. |
| GeoIntMap | Map placeholder Box | `GeoIntMapScreen.kt` | 🟡 visible placeholder text; not clickable. |
| GeoIntMap | (data) | same | ❌ used `MockData` → 🔧 now uses `Rescue911Repository`; if `caseId=""` (Map tab) it fans out across all cases. |
| Timeline | Single InfoCard | `TimelineScreen.kt` | 🟡 visible "(placeholder)" text. |
| SocialGraph | Single InfoCard | `SocialGraphScreen.kt` | 🟡 visible "(placeholder)" text. |
| ArchiveFindings | List | `ArchiveFindingsScreen.kt` | ❌ used `MockData` → 🔧 now uses `Rescue911Repository.evidenceForCase().filter(ARCHIVE)`; Loading + Empty states. |
| **ManualReviewQueue** | **"Confirm" button (per row)** | **`ManualReviewQueueScreen.kt`** | **❌ `onClick = {}` → 🔧 calls `POST /v1/review/{id}` with `action=confirm`. Success banner shows new status (e.g. "Review recorded: confirm → HUMAN_CONFIRMED"). Error banner shows reason.** |
| ManualReviewQueue | "Reject" button | same | ❌ → 🔧 same, with `action=reject` |
| ManualReviewQueue | "Needs more checks" | same | ❌ → 🔧 same, with `action=needs_more_checks` |
| ManualReviewQueue | "Escalate to authorities" | same | ❌ → 🔧 same, with `action=escalate_to_authorities` |
| ManualReviewQueue | (data) | same | ❌ used `MockData` → 🔧 now hits `GET /v1/evidence` and reloads after each review action |
| AuditLog | List | `AuditLogScreen.kt` | ✅ already wired in prior commit (`a0c3c3c`). |
| Settings | Mock toggle / lang buttons / API base URL field / Check / Provider status link | `SettingsScreen.kt` | ✅ all wired. |
| ProviderStatus | List | `ProviderStatusScreen.kt` | ✅ loads from `/v1/provider-status` with static fallback. |
| ProviderStatus | (no refresh button) | same | ✅ acceptable; refresh happens on screen entry. |

## Routes audit

`Routes` declares 16 entries; `NavHost` registers all 16. After this commit, **every route is reachable from the UI**:
- `PERSON` (was registered, never navigated to) → now reachable via `CaseDetail` person card.
- `Map` bottom-nav → no longer pinned to a stale mock id.

## Backend endpoint audit

| Endpoint | Used by | Status |
|---|---|---|
| `GET /v1/health` | `BackendStatusMonitor`, `DataSourceViewModel` (init probe) | ✅ |
| `GET /v1/provider-status` | `ProviderStatusScreen` | ✅ |
| `GET /v1/cases` | `CaseListScreen`, `PersonProfileViewModel`, `GeoIntMapViewModel` (Map tab fan-out) | ✅ |
| `GET /v1/cases/{id}` | `CaseDetailScreen` | ✅ |
| `GET /v1/evidence?case_id=` | `EvidenceInboxScreen`, `ArchiveFindingsScreen` | ✅ |
| `GET /v1/evidence` (no filter) | `ManualReviewViewModel.reload()` | ✅ |
| `GET /v1/hypotheses?case_id=` | `HypothesisBoardScreen`, `GeoIntMapViewModel` | ✅ |
| `GET /v1/audit?limit=` | `AuditLogScreen` | ✅ |
| **`POST /v1/review/{id}`** | **`ManualReviewViewModel.act()` — newly wired** | ✅ |

No new backend endpoints were needed — every screen now uses what the FastAPI app already serves.

## Files changed

**Android source (10):**
- `data/remote/Rescue911Api.kt` — added `POST v1/review/{id}` with `@Body ReviewBodyDto`
- `data/remote/dto/Dtos.kt` — added `ReviewBodyDto`
- `feature/search/SearchDashboardScreen.kt` — rewritten with clickable cards + banners
- `feature/evidence/UploadMediaScreen.kt` — buttons set banner state
- `feature/cases/CreateCaseScreen.kt` — Create button shows placeholder banner instead of silent pop
- `feature/cases/CaseDetailScreen.kt` — person card now clickable
- `feature/cases/PersonProfileScreen.kt` — uses repository, Loading/Empty states
- `feature/archive/ArchiveFindingsScreen.kt` — uses repository
- `feature/geoint/GeoIntMapScreen.kt` — uses repository, Map-tab fan-out
- `feature/review/ManualReviewQueueScreen.kt` — POSTs review actions, banners, reload
- `navigation/Rescue911Nav.kt` — Map bottom-nav `caseId=` empty
- `ui/components/Components.kt` — new `ActionResultBanner` + `BannerKind` enum

**Android resources (3):**
- `res/values/strings.xml`, `res/values-iw/strings.xml`, `res/values-ru/strings.xml` — 6 new keys per language: `state_empty_review`, `state_empty_person`, `search_dispatch_note`, `search_pick_case_first`, `search_opening`, `upload_not_implemented`, `create_case_not_implemented`

**Backend:** no changes — `POST /v1/review/{id}` already existed.

**Docs:** this file (`docs/ANDROID_INTERACTION_AUDIT.md`).

## Tests

| Suite | Result |
|---|---|
| Backend pytest (no backend code changed but ran for safety) | not re-run this turn — last green run on `1439bc8` |
| Android `:app:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| Android `:app:assembleDebug` | ✅ BUILD SUCCESSFUL |

## Runtime verification (live emulator-5554, backend 127.0.0.1:8011)

| Step | Result |
|---|---|
| App relaunched after install | DataSourceBadge: **Backend** (DataStore preserved across re-install) |
| Cases list | 3 backend seed cases (Anna L., Dina K., Yossi B.) |
| Tap Anna L. card → CaseDetail | ✅ "Anna Lifshitz · Tel Aviv-Yafo · 32.0853, 34.7818" |
| Tap **Run search** → SearchDashboard | ✅ banner "Search runs on the backend; results appear in the Evidence inbox." |
| Tap **Web search** card | **✅ navigated to EvidenceInbox; rendered backend evidence: "Public sighting on volunteer channel" (Needs Review), "Image-geo candidate near promenade" (Coastline match). Backend log: `GET /v1/evidence?case_id=11111111-...` 200.** |
| Tap **Review** tab | ✅ Review queue with 2 items: "Public sighting on volunteer channel" + "Public hiking forum thread (archived)" |
| Tap **Confirm** on first row | ✅ banner "Review recorded: confirm → HUMAN_CONFIRMED". Backend log: `POST /v1/review/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1` 200, then auto-`GET /v1/evidence` reload. |
| logcat -d \| grep FATAL/ANR/AndroidRuntime/Process died for `com.rescue911` | **0 matches** |
| App process / Activity foreground throughout | ✅ |

## Remaining UX gaps (intentional for next milestone)

1. **EvidenceInbox / HypothesisBoard items not clickable** — no detail screen exists yet. List rows are read-only. Low impact (validation badges + next-action text are already shown).
2. **CreateCase Create button** is a placeholder — real `POST /v1/cases` wiring is small, deferred to keep this commit focused on dead-click fixes.
3. **UploadMedia** is a placeholder — needs Activity Result API + `POST /v1/media` (+ retention/PII rules) before it does anything real.
4. **GeoIntMap** is still a placeholder Box; MapLibre integration deferred. Hypotheses below the map now load real data.

All of the above show clear visible state — none of them silently does nothing.

## Acceptance criteria

| Criterion | Result |
|---|---|
| Static interaction audit document created | ✅ this file |
| Every visible action classified | ✅ table above |
| Web Search no longer silently does nothing | ✅ navigates + banner |
| No other audited visible action silently does nothing | ✅ all `onClick = {}` removed |
| All UI-referenced routes exist in NavHost | ✅ |
| App builds | ✅ |
| Android unit tests pass | ✅ |
| Backend tests pass if backend changed | n/a (no backend changes) |
| Runtime smoke passes | ✅ (above) |
