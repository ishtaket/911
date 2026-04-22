# SearchAid — Product Definition

## Summary
Mobile and server system for early missing-person response that helps families and volunteers start a structured search as quickly as possible, gather digital leads, and build a probability-based search map.

## Core Idea
The system does NOT rely on the missing person having a phone. It works through:
- Quick "missing" case activation
- Person profile with historical locations
- Digital search across open sources
- Social network and public group search
- Alias, nickname, email search
- Witness report collection
- Search map and action coordination

## Key Value
Reduce the time between disappearance and structured search launch.

## Target Scenarios
- Person with Alzheimer's left home
- Person with dementia is disoriented
- Mentally ill person went missing
- Elderly person left without a phone
- Family doesn't know where to search in the first minutes/hours

## System Flow
1. Operator opens profile
2. Presses "Missing"
3. Enters last seen location + clothing + notes
4. System builds Identity Pack
5. System loads historical places and family notes
6. System generates search queries
7. System saves web/social/messenger leads
8. Signal Engine calculates search zones
9. Search Map displays the map
10. Operator and search team mark checks and witness reports
11. Zones update dynamically

## Core Entities

### PersonProfile
- id, name, age, photo, condition/diagnosis
- distinguishing features, habits
- knownLocations, historicalPlaces
- aliases, nicknames, emails, phones
- familyNotes

### MissingCase
- id, personId, status (ACTIVE/FOUND/CLOSED)
- createdAt, lastSeenTime, lastSeenLocation
- clothesDescription, notes, operatorId

### HistoricalPlace
- id, personId, title, lat, lon, source, note

### SocialSource
- id, platform, sourceType, title
- handleOrAlias, region, url
- visibility, enabled

### SearchLead
- id, caseId, type (WEB/SOCIAL/MESSENGER/WITNESS/MANUAL)
- platform, matchedValue, textSnippet
- possibleLocationName, lat/lon (optional)
- timestamp (optional), confidence, status (NEW/CONFIRMED/REJECTED/ARCHIVED)

### WitnessReport
- id, caseId, sourceName, sourceType
- text, timestamp
- possibleLocationName, lat/lon (optional)
- confidence, status (NEW/VERIFIED/REJECTED)

### SearchZone
- id, caseId, lat, lon, radius
- score, reason

### AuditLog
- id, action, caseId (optional)
- details, timestamp

## Modules

### 1. Profile Module
Create/edit person profiles with photos, historical places, aliases, emails, phones, family notes.

### 2. Alert / Missing Case Module
"Missing" button, last seen input, case lifecycle (ACTIVE → FOUND/CLOSED).

### 3. Identity Engine
Normalize names, build identity pack, store aliases/nicknames/emails/phones, generate search query variants.

### 4. Open Web Search Module
Search by name, alias, nickname, email, name+city/district. Manual save of useful web leads.

### 5. Social Network Search Module
Store marked social networks, search public profiles and groups, search mentions by aliases, equal priority for all enabled platforms, save found leads.

### 6. Messenger Outreach Module
Contact and group lists, message templates, send journal, manual/semi-automatic outreach, witness response tracking.

### 7. Signal Engine
Aggregate signals from: family, historical places, web search, social networks, messengers, witness reports. Calculate scores, generate search zones, update probability map.

### 8. Search Map Module
Show: last seen, historical places, leads, witness reports, search zones. Mark areas as "checked."

### 9. Audit / Log Module
Full action journal: who searched, what queries ran, which groups were contacted, which leads were confirmed/rejected.

## MVP Screens
1. Profiles List
2. Person Profile
3. Create / Edit Profile
4. Start Missing Case
5. Active Case Dashboard
6. Search Map
7. Leads List
8. Witness Reports
9. Outreach Screen
10. Audit Log

## MVP Use Cases
- CreatePersonProfile
- UpdatePersonProfile
- StartMissingCase
- CloseMissingCase
- BuildIdentityPack
- GenerateSearchQueries
- AddHistoricalPlace
- AddLead
- AddWitnessReport
- GenerateSearchZones
- MarkZoneChecked
- SendOutreachMessage
- SyncCase
- RecalculateSignals

## Non-Functional Requirements
- Offline-first
- Case startup: max 60 seconds to first usable search screen
- All actions are logged
- Secure storage of sensitive data
- Manual operator control
- No hidden automatic activity beyond defined rules

## Boundaries

### What the project DOES
- Works with open and permitted sources
- Uses system's own social media accounts
- Supports manual and semi-automatic lead collection
- Helps coordinate search

### What the project does NOT do
- Does not hack accounts
- Does not read private messages without access
- Does not access closed email
- Does not use illegal databases or leaks
- Does not promise "magically find a person" — increases probability and speed

## Tech Stack

### Client
- Kotlin + Jetpack Compose + Material 3
- MVVM + StateFlow + Navigation
- Hilt DI
- Room (offline-first)
- Google Maps SDK
- WorkManager for background tasks

### Backend (planned)
- Firebase Auth, Firestore, Storage, FCM, Cloud Functions
- Kotlin Ktor or Node.js/NestJS
- REST API + background workers

### Search layer (planned)
- Search orchestrator
- Normalization engine
- Query builder
- Lead scoring service

## MVP Roadmap

### Phase 1 (current)
Profile, missing case, historical places, aliases, manual leads, witness reports, search map, audit log, three-level verification baseline.

### Phase 2
Web query builder, social source registry, messenger outreach, signal scoring, witness intake automation.

### Phase 3
Semi-automation, richer ranking, map heat zones, advanced scenario verification.

## Three-Level Verification Rule

Every feature is considered incomplete until it passes:
1. **Level 1 — Logic Verification**: unit tests for pure logic, use cases, scoring
2. **Level 2 — Integration Verification**: Room+Repository, ViewModel+UseCase, API tests
3. **Level 3 — Runtime Verification**: UI tests, end-to-end scenarios, manual QA

No merge to main without all three levels covered.
