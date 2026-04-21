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
- **PersonProfile** — name, age, photo, diagnosis, habits, known locations, aliases, emails, phones
- **MissingCase** — person, status, last seen, clothing, notes
- **HistoricalPlace** — title, coordinates, source
- **SocialSource** — platform, handle, region, visibility
- **SearchLead** — type, platform, matched value, confidence, status
- **WitnessReport** — source, text, possible location, confidence
- **SearchZone** — coordinates, radius, score, reason

## Modules
1. **Profile Module** — create/edit person profiles with photos, places, aliases
2. **Alert / Missing Case Module** — "missing" button, last seen, case lifecycle
3. **Identity Engine** — normalize names, build search query variants
4. **Open Web Search Module** — search by name/alias/email across open web
5. **Social Network Search Module** — search public profiles and groups
6. **Messenger Outreach Module** — template messages, contact lists, send tracking
7. **Signal Engine** — aggregate signals, score leads, generate search zones
8. **Search Map Module** — visualize all data on map with zone checking
9. **Audit / Log Module** — full action journal

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

## MVP Roadmap
### Phase 1
Profile, missing case, historical places, aliases, manual leads, search map, audit log, three-level verification baseline

### Phase 2
Web query builder, social source registry, messenger outreach, signal scoring, witness intake

### Phase 3
Semi-automation, richer ranking, map heat zones, advanced scenario verification
