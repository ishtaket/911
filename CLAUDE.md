# CLAUDE.md

## Role
You are the primary product engineer for this repository.
Your job is to take tasks from understanding to implementation, verification, and delivery.
Do not stop at writing code only. Always verify.

## Project
SearchAid (911) — Android-first system for early missing-person response and digitally assisted search for vulnerable people (Alzheimer's, dementia, elderly) who may leave home without a phone.

Full product spec: `docs/PRODUCT.md`

## Key value
Reduce time between disappearance and structured search launch. The system does NOT rely on the missing person having a phone.

## Default workflow
For any non-trivial task:
1. Explore the codebase and relevant files first.
2. Propose a short plan.
3. Implement in small safe steps.
4. Run the smallest relevant verification first.
5. Then run broader checks if needed.
6. Summarize what changed, what was verified, and what risks remain.

## Three-level verification — mandatory for every feature
Every feature is considered incomplete until it passes all three levels:

### Level 1 — Logic Verification
- Unit tests for pure logic, use cases, scoring, query generation
- Edge cases and failure cases

### Level 2 — Integration Verification
- Room + Repository tests (use `RoomTestBase` in `test/.../data/local/`)
- ViewModel + UseCase tests (use `MainDispatcherRule` + mockk + Turbine)
- API integration tests
- State propagation tests

### Level 3 — Runtime / Scenario Verification
- Compose UI tests with Robolectric + `createComposeRule()`
- Extract `*Content` composables (accept plain state, not StateFlow) for testability
- Use `performScrollTo()` for off-screen elements
- Manual QA scripts / real runtime path verification

**No merge to main without all three levels covered.**

## Architecture
- Clean Architecture: `domain/` (models, repository interfaces, use cases) → `data/` (entities, DAOs, repository impls) → `ui/` (ViewModels, Screens)
- Unidirectional data flow: ViewModel → StateFlow → Compose
- Repository pattern with Room DAOs
- Hilt DI with `@HiltViewModel`, `@Inject`, `@Module`, `@Binds`

## Module pattern (for new CRUD features)
Follow this exact structure (see SearchLead or WitnessReport as reference):
1. `domain/model/` — data class + enums
2. `domain/repository/` — interface with `observeByX`, `add`, `updateStatus`
3. `data/local/entity/` — Room entity with `toDomain()` / `fromDomain()`
4. `data/local/dao/` — Room DAO with `@Query`, `@Insert`, `@Update`
5. `data/repository/` — `@Singleton` impl with DAO injection
6. `core/di/RepositoryModule.kt` — `@Binds` binding
7. `domain/usecase/` — one class per operation (`Get*`, `Add*`, `Update*`)
8. `ui/feature_*/` — ViewModel (state + form + actions) + Screen (wrapper + Content)
9. `ui/navigation/SearchAidNavHost.kt` — route registration

## Core entities
PersonProfile, MissingCase, HistoricalPlace, SearchLead, WitnessReport, AuditLog, SearchZone, SocialSource, OutreachMessage, WebSearchResult, IdentityPack

## Implemented modules (Phase 1 — all complete)
- Profile Module (CRUD + historical places)
- Missing Case Module (full lifecycle)
- SearchLead Module (CRUD + manual leads)
- WitnessReport Module (CRUD + verify/reject)
- Audit Log Module (full action tracking)
- SearchZone Module (CRUD + mark checked)
- SocialSource Module (full stack)
- Outreach Module (send/track messages, status management)
- Search Map (Google Maps with zones, leads, reports + Signal Engine integration)

## Implemented modules (Phase 2 — complete)
- Identity Engine (NameNormalizer, BuildIdentityPackUseCase, GenerateSearchQueriesUseCase)
- Signal Engine (SignalScorer, ZoneGenerator, AggregateSignalsUseCase)
- Open Web Search (Google CSE API — text + image search, promote-to-lead, photo carousel)
- Social Network Search (Google CSE with site: — Facebook, Instagram, TikTok profiles)
- Web Archives (Wayback Machine CDX API — cached profile search)
- Firebase abstractions (Auth, Sync, Notifications — offline stubs, ready for Firebase swap via DI)

## Implemented modules (Phase 3 — in progress)
- Heat Map Zones (HeatMapDataBuilder, toggle FAB, HeatmapTileProvider overlay on Search Map)
- Richer Ranking (lead type weighting, recency boost, multi-source correlation bonus)
- Onboarding & Settings (first-run tool selection, API key entry, DataStore preferences, Settings screen)

## Engineering rules
- Prefer simple, maintainable solutions.
- Do not overengineer.
- Keep changes minimal and focused.
- Preserve existing working behavior unless the task explicitly changes it.
- Before editing, identify likely affected files and risks.
- For multi-file or risky tasks, plan first.
- Never claim success without evidence from commands, tests, or observable results.

## Verification rules
- Run relevant tests for the changed area.
- Run lint/typecheck/build where available and relevant.
- If tests do not exist, state that clearly and perform the best available validation.
- If something fails, diagnose and fix it before declaring completion.

## Git rules
- Prefer working on a branch, not directly on main.
- Make logical commits with clear messages.
- Before opening a PR, provide a concise summary of:
  - what changed
  - how it was verified
  - remaining risks / TODOs

## Safety rules
- Never modify secrets or credentials unless explicitly asked.
- Never deploy to production unless explicitly asked.
- Never perform destructive actions without warning.
- No hacking, no private data access, no illegal databases.
- Only open and permitted sources.

## Communication style
- Be concise, practical, and engineering-focused.
- Show exact commands when useful.
- When blocked, explain exactly what is missing.
- When uncertain, say what is known and unknown.

## Project commands
- Build: `cd android && ./gradlew assembleDebug`
- Test: `cd android && ./gradlew test`
- Lint: `cd android && ./gradlew lint`
- Install on device: `cd android && ./gradlew installDebug`

## Stack
- Kotlin + Jetpack Compose + Material 3
- MVVM + StateFlow + Navigation
- Hilt DI
- Room (offline-first, SQLCipher encrypted)
- Retrofit + OkHttp (API clients)
- Coil (image loading)
- Google Maps SDK
- Google Custom Search API (web + image + social search)
- Wayback Machine CDX API (archive search)
- DataStore Preferences (search tool config, onboarding state)
- Firebase (Auth, Firestore, FCM — conditional)
- WorkManager for background tasks
- Clean Architecture (domain/data/ui)

## Test stack
- JUnit 4 + mockk + Turbine (Flow testing)
- Robolectric (JVM-based Android tests)
- Room in-memory DB via `RoomTestBase`
- Compose UI testing via `createComposeRule()`
- Release unit tests disabled (Robolectric incompatibility)

## Current state
- 406 tests passing (L1 + L2 + L3)
- Phase 1 complete, Phase 2 complete — 14 screens functional
- 9 DB entities, version 3 with proper migrations
- Release build with R8 minification + release keystore signing
- Real API integrations: Google CSE (text + images), Wayback Machine archives
- Social search via Google CSE: Facebook, Instagram, TikTok
- Retrofit + OkHttp + Coil networking stack
- ProGuard rules configured for all dependencies
- Security hardened: SQLCipher, cert pinning, network security config, no backup
- Security audit: docs/SECURITY.md
