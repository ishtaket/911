# Project Spec

## Product

Israel-focused OSINT + GeoINT missing-person search platform.
Operator UI: native Android. Brains: secure backend API.

## Primary users

- Field volunteers searching for missing people.
- Analysts reviewing leads.
- Authorized coordinators escalating to authorities.

## Goal

Help authorized operators collect, normalize, validate, and review **public** evidence related to missing-person cases inside Israel — fast, lawfully, with explicit confidence and human confirmation.

## Default scope

- Geography: **Israel**.
- Languages: **English, Hebrew (RTL), Russian** (Arabic place-name variants for queries where useful).

## Core capabilities

1. Case intake (Android).
2. Search variant generation (backend, multilingual + Israel-aware).
3. Public web search (Brave / Google CSE / SerpAPI / DataForSEO providers + mock).
4. Public social search (FB / IG / TT / YT / TG / Reddit / X / VK / LinkedIn providers + mock).
5. Archive / indexed deleted-information search (Wayback CDX, Common Crawl CDXJ, snippet, public-mirror + mock).
6. GeoINT image/video analysis (EXIF, OCR, Vision, GeoSeer, Picarta, Maps/POI validation, OpenAI Vision reasoner + mock).
7. Evidence normalization (single `provider_result` schema, dedup, content-hash).
8. Entity resolution (people, places, social handles).
9. Hypothesis scoring (location candidates, ranked, with contradictions).
10. Three-level validation (automated → cross-source → human).
11. Multilingual rescue-themed Android UI (EN / HE-RTL / RU).
12. Audit / privacy / security (RBAC skeleton, audit log on every search and provider call).

## Non-goals

- Hacking.
- Private-account access.
- Leaked / stolen datasets.
- Automated contacting of people.
- Unverified final conclusions.
- Web frontend in this milestone (deferred).

## Acceptance for milestone 1

- Android app builds (debug APK) when SDK is available.
- Backend API serves health, mock case, mock search, mock GeoINT.
- Schemas + provider interfaces + mock providers in place.
- Local Docker Compose runs (Postgres + Redis + MinIO at minimum).
- Unit + mocked-provider + smoke tests defined.
- EN / HE / RU strings stubbed.
- Final report documents what is built, what is mocked, and what is blocked.
