# Project audit — 2026-04-25

Read-only audit. No files were deleted, restored, reset, committed, or overwritten.

## 1. Executive summary

**The project is still aligned with the Android-first, server-ready, Israel-focused OSINT + GeoINT mission.**

- The new direction (`com.rescue911.osint` Android app + FastAPI backend + Docker infra) is fully scaffolded and present in the working tree.
- The legacy `com.searchaid` app remains intact in the HEAD commit `6785552` (its files appear as staged-deletion in the worktree but were never committed). Recovery: `git restore -- android/`.
- Docs, agents, skills, and prompts all describe the new architecture correctly.
- Backend pytest: **19 / 19 passed**.
- Compose local: **valid**. Compose staging: skeleton — requires real env file (by design, `${VAR:?missing}`).
- No hardcoded API keys found anywhere under `backend/app` or `android/app/src`.
- Android does **not** import or call any external provider — only `Rescue911Api` (Retrofit, our backend).
- One real risk: `.gitignore` is currently in deleted state in the worktree. Any future bulk `git add .` could pick up `backend/.venv/`, gradle build outputs, and other non-source artifacts.

Did Claude forget what we are building? **No.** The repo direction is consistent across CLAUDE.md, .claude/ROUTER.md, MASTER_BUILD_PROMPT.md, PROJECT_SPEC.md, ARCHITECTURE.md, and FINAL_REPORT.md.

## 2. Repo state

- **Branch:** `feat/heat-map-zones`
- **Counts:** 295 changes total — 242 deletions (all SearchAid files), 19 modified, 34 untracked, 0 staged for commit.
- **HEAD:** `6785552 feat: add onboarding wizard and settings screen with search tool preferences`
- **SearchAid:** preserved in HEAD; deletions are uncommitted and recoverable.
- **Rescue911 scaffold:** present in the worktree as untracked files under `backend/`, `infra/`, `scripts/`, plus `android/app/src/main/java/com/rescue911/`, `docs/*` (12 new docs), `.claude/` agents/skills/prompts, `android/gradle/libs.versions.toml`, `android/app/src/test/java/com/rescue911/`, `android/app/src/androidTest/`, `android/app/src/main/res/values-iw/`, `android/app/src/main/res/values-ru/`.
- **Risky uncommitted changes:** none destructive. `.gitignore` deletion is the one item to watch — see Security section.

## 3. Environment

| Tool | Status | Notes |
|---|---|---|
| Docker CLI | OK | 29.4.0 |
| Docker Compose | OK | v5.1.2 |
| Docker daemon | OK | running |
| Python | OK | 3.12.0 |
| Java (`java` on PATH) | WARN | `1.8.0_351` (JRE 8) — incompatible with Android tools |
| `JAVA_HOME` | WARN | unset; recommend `C:\Program Files\Java\jdk-19` |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | OK | `C:\Android\Sdk` |
| Android cmdline-tools / platform-34 / build-tools-34 | OK | installed |
| Android emulator | WARN | not installed |
| ADB | OK | 1.0.41 |

The build script (`scripts/dev_android_build.ps1`) compensates for the Java/PATH trap by setting `JAVA_HOME` and prepending `%JAVA_HOME%\bin`. `:app:assembleDebug` last succeeded on 2026-04-25 per `LOCAL_ENVIRONMENT.md`.

## 4. Backend

- **Files present:** `app/main.py`, `app/config.py`, 9 schema modules, 9 route modules, 8 service modules, provider registry, 5 provider categories with 34 modules total, 6 test modules, `Dockerfile`, `requirements.txt`.
- **Endpoints present:** `/v1/health`, `/v1/cases`, `/v1/media`, `/v1/search`, `/v1/geoint`, `/v1/evidence`, `/v1/hypotheses`, `/v1/review`, `/v1/audit` — all wired in `main.py`.
- **Provider mocks present:** 1 mock per category — web, social (9 networks), archive, geoint, maps. Real provider stubs exist behind the same interfaces; registry switches on `MOCK_PROVIDERS` and on whether the relevant key is configured.
- **Pydantic schemas present:** case, person, query, provider_result, evidence, hypothesis, validation, audit, geoint.
- **Three-level validation:** present in `services/validation_service.py` — `review_evidence` writes `ValidationLevel3`, sets `EvidenceStatus`, and emits an audit log entry. `confirm` → `HUMAN_CONFIRMED`. No code path sets `HUMAN_CONFIRMED` without a Level-3 action.
- **Tests run:** `backend/.venv/Scripts/python.exe -m pytest -q` → **19 passed** (test_health, test_schemas, test_query_builder, test_providers_mock, test_validation, test_api_flow). Warnings only (deprecated `datetime.utcnow()` and one unset `asyncio_default_fixture_loop_scope`).
- **Hardcoded keys:** none found. All keys come from env via `app/config.py:Settings`.
- **Blockers:** none for milestone 1. For milestone 2: SQLAlchemy ORM models + Alembic migrations are not yet wired to a real DB (in-memory store is in use).

## 5. Android

- **Package:** `com.rescue911.osint` (manifest declares `.MainActivity`, `.Rescue911App`).
- **16 screens present** (verified by `verify_project_scaffold.ps1`): CaseList, CreateCase, CaseDetail, PersonProfile, UploadMedia, SearchDashboard, EvidenceInbox, HypothesisBoard, GeoIntMap, Timeline, SocialGraph, ArchiveFindings, ManualReviewQueue, AuditLog, Settings, ProviderStatus.
- **Theme + nav:** `ui/theme/Color.kt`, `Theme.kt`, `navigation/Rescue911Nav.kt`, shared `ui/components/Components.kt` present.
- **i18n:** `values/strings.xml` (EN), `values-iw/strings.xml` (Hebrew), `values-ru/strings.xml` (Russian) all present and populated. Manifest sets `android:supportsRtl="true"` and `android:configChanges` includes `locale|layoutDirection`.
- **Backend interface:** `data/remote/Rescue911Api.kt` is a Retrofit interface that talks only to our backend (`v1/health`, `v1/cases`, `v1/search/start/{caseId}`, `v1/evidence`, `v1/hypotheses`, `v1/audit`).
- **No external provider keys on Android.** No imports/usages of OpenAI, Brave, Google, Picarta, GeoSeer, etc. found under `android/app/src`. `AppPreferences` stores only `api_base_url`, `language`, `mock_mode` — no API keys.
- **Local cache:** DataStore preferences only. Room is listed in `CLAUDE.md` stack but not yet scaffolded — TODO documented in `FINAL_REPORT.md` next-milestone item 5 (Room + EncryptedSharedPreferences).
- **Tests:** unit tests under `android/app/src/test/java/com/rescue911/`; `androidTest/` directory present.
- **Build readiness:** `:app:assembleDebug` and `:app:testDebugUnitTest` already passed in the previous session per `LOCAL_ENVIRONMENT.md`. No re-run attempted in this audit (read-only).

## 6. Infra and CI

- `infra/docker-compose.local.yml` — **valid** (`docker compose config --quiet` exit 0). Postgres+PostGIS, Redis, MinIO are core; OpenSearch and Qdrant are profile-gated; backend image is `--profile full`.
- `infra/docker-compose.staging.yml` — skeleton; requires `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` via env (intentional `${VAR:?missing}`). `config --quiet` errors without an env file — by design.
- `infra/nginx/staging.conf` present.
- 12 PowerShell scripts present and the two read-only checks ran cleanly (`verify_project_scaffold.ps1` passed, `check_local_environment.ps1` PASS with two WARNs).
- `.github/workflows/ci.yml` — backend pytest job + android-lint config-validation job. Full Android Gradle build is gated on a follow-up workflow that materializes the SDK and wrapper jar in CI.

## 7. Security / safety audit

- **Hardcoded secrets:** none found in `backend/app` or `android/app/src` for OpenAI / Google / Picarta / GeoSeer / Brave / Azure / YouTube / Telegram / Meta / Reddit. `.env.example` has all key fields blank.
- **Android provider-key risk:** none. Android imports no external provider SDK or HTTP client beyond Retrofit-to-our-backend. `AppPreferences` does not store any API key.
- **Unsafe scraping:** none. Forbidden boundary terms ("hack", "bypass private", "leaked", "stolen", "scrape private", "fake identity", "auto contact") only appear in policy / boundary docs, never in implementation under `android/`.
- **Three-level validation enforced in code:** `services/validation_service.py` is the only path that promotes evidence to `HUMAN_CONFIRMED`, and it requires an explicit `ValidationLevel3Action`.
- **Backend CORS:** `allow_origins=["*"]` in `app/main.py` — explicitly noted as dev-only in the code comment, expected to be tightened by reverse proxy in staging/prod. Acceptable for milestone 1.
- **`.gitignore` is in deleted state in the worktree.** This is the highest-priority safety item: a future bulk `git add .` would stage `backend/.venv/` (hundreds of MB), gradle build outputs, and `.env`-style files. Re-creating `.gitignore` (or restoring from HEAD) before the next commit is strongly advised.
- **Backend security TODOs not yet implemented (documented as milestone 2):** no auth/RBAC on `/v1/audit` and other routes, no Alembic migrations, no SQLCipher / EncryptedSharedPreferences on Android, no rate limiting / WAF.

## 8. Next exact steps

Recommended order, smallest-blast-radius first.

**Step 1 — protect the worktree before any commit.**
- Restore `.gitignore` from HEAD so subsequent `git add` commands cannot accidentally stage `.venv/`, build outputs, or local secrets:
  - `git restore .gitignore`
- Inspect what would be added if anything: `git status --short`.

**Step 2 — decide the SearchAid → Rescue911 split.**
Three viable options; pick one and execute deliberately, **do not let them mix in one commit**:
- (a) Save SearchAid on its own branch and commit Rescue911 to `feat/heat-map-zones`:
  - `git switch -c archive/searchaid-snapshot`
  - `git switch feat/heat-map-zones`
  - then commit Rescue911 (see Step 3)
- (b) Keep SearchAid in HEAD and add Rescue911 side-by-side as untracked-then-tracked under its new paths only.
- (c) Replace SearchAid entirely (commit the deletions plus the new scaffold) — only if you are sure you will not need SearchAid again.

**Step 3 — commit the Rescue911 scaffold in reviewable chunks.**
- `git add backend/ infra/ scripts/ docs/ .claude/ .env.example .github/workflows/ci.yml CLAUDE.md README.md`
- `git add android/app/src/main/java/com/rescue911/ android/app/src/main/res/values-iw/ android/app/src/main/res/values-ru/ android/app/src/test/java/com/rescue911/ android/app/src/androidTest/ android/gradle/libs.versions.toml android/.gitignore android/README.md android/app/src/main/res/xml/backup_rules.xml android/app/src/main/res/xml/data_extraction_rules.xml`
- Stage modifications you intend to keep: `android/app/build.gradle.kts`, `android/build.gradle.kts`, `android/settings.gradle.kts`, `android/gradle.properties`, `android/gradle/wrapper/gradle-wrapper.properties`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml|themes.xml|colors.xml`, `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`, `docs/SECURITY.md`, `.github/workflows/ci.yml`.
- Verify nothing sensitive is staged: `git diff --cached | head` and check for `.env`, `.venv/`, `*.apk`, `*.keystore`.

**Step 4 — run the green checks before committing.**
- `backend/.venv/Scripts/python.exe -m pytest -q` (already 19/19 green).
- `powershell.exe -ExecutionPolicy Bypass -File scripts\verify_project_scaffold.ps1` (already PASS).
- Optionally re-run `:app:assembleDebug` and `:app:testDebugUnitTest` — the SDK is in place and the prior run was green.

**Step 5 — open milestone-2 tickets for known TODOs.**
- DB persistence: SQLAlchemy ORM + Alembic for case / person / evidence / hypothesis / audit.
- Auth + RBAC: bearer tokens; lock down `/v1/audit`, `/v1/review`.
- Android Room + DataStore session token + EncryptedSharedPreferences.
- MapLibre Android on `GeoIntMapScreen`.
- Real provider implementations (Wayback CDX first — already partially live).
- CI: provision Android SDK in workflow to run `assembleDebug` and emulator-backed instrumented tests.
- Tighten backend CORS per environment.

**Commands you can run as-is right now:**

```powershell
# Safety
git restore .gitignore
git status --short

# Verify
backend\.venv\Scripts\python.exe -m pytest -q
powershell.exe -ExecutionPolicy Bypass -File scripts\verify_project_scaffold.ps1
docker compose -f infra\docker-compose.local.yml config --quiet
```

---

*Audit produced by Claude Code on 2026-04-25. Read-only — no code or git state was modified.*
