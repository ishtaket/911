# SearchAid (911)

Android-first system for early missing-person response and digitally assisted search for vulnerable people who may leave home without a phone.

## What it does
Helps families and volunteers quickly activate a structured search when a vulnerable person (Alzheimer's, dementia, elderly) goes missing. Collects digital leads from open sources, builds a probability-based search map, and coordinates search actions.

## Status
Phase 1 + Phase 2 complete. 12 screens functional, 364 tests passing (L1 unit + L2 integration + L3 UI), release build with R8 minification and signing verified.

## Docs
- [Product definition](docs/PRODUCT.md)

## Key design principles
- **Offline-first** — works without internet in the field
- **Speed** — max 60 seconds from case activation to first usable search screen
- **Legal and ethical** — only open and permitted sources
- **Operator control** — no hidden automatic activity
- **Full audit trail** — all actions are logged

## Getting started
```bash
cd android && ./gradlew assembleDebug
```
Or open `android/` in Android Studio.

### API keys (optional)
Web and social search features require Google Custom Search API credentials. Add to `android/local.properties`:
```properties
GOOGLE_CSE_API_KEY=your_api_key
GOOGLE_CSE_CX=your_search_engine_id
```
The app works without these keys — search features gracefully return empty results.

### Testing
```bash
cd android && ./gradlew testDebugUnitTest  # 364 tests
cd android && ./gradlew lint               # lint check
cd android && ./gradlew assembleRelease    # signed release APK
```

## Tech stack
- Kotlin + Jetpack Compose + Material 3
- Hilt DI
- Room (offline-first, 9 entities, versioned migrations)
- Retrofit + OkHttp (Google CSE API, Wayback Machine CDX API)
- Coil (image loading)
- Google Maps SDK
- Firebase (Auth, Firestore, FCM — abstracted with offline stubs)
- WorkManager
- Clean Architecture (domain/data/ui)

## Modules
- **Profile** — person profiles with historical places
- **Missing Case** — case lifecycle management
- **Search Lead** — lead tracking (manual + promoted from search)
- **Witness Report** — field reports with verify/reject workflow
- **Audit Log** — full action tracking
- **Search Zone** — probability-scored search areas
- **Social Source** — social account management per person
- **Outreach** — message sending and status tracking
- **Search Map** — Google Maps with zones, leads, witness reports, Signal Engine
- **Web Search** — Google CSE text + image search, Wayback Machine archives
- **Social Search** — Facebook, Instagram, TikTok profile search via Google CSE
- **Identity Engine** — name normalization, identity pack building, query generation
- **Signal Engine** — signal scoring, clustering, auto zone generation
