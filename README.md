# SearchAid (911)

Android-first system for early missing-person response and digitally assisted search for vulnerable people who may leave home without a phone.

## What it does
Helps families and volunteers quickly activate a structured search when a vulnerable person (Alzheimer's, dementia, elderly) goes missing. Collects digital leads from open sources, builds a probability-based search map, and coordinates search actions.

## Status
Phase 1 MVP complete. All 10 screens functional, 224 unit/integration/UI tests passing, release build with R8 minification verified.

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

## Tech stack
- Kotlin + Jetpack Compose + Material 3
- Hilt DI
- Room (offline-first, 9 entities)
- Google Maps SDK
- WorkManager
- Clean Architecture (domain/data/ui)
