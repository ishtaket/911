# Play Console Form Answers

## Step 7 — Content Rating (IARC Questionnaire)

Answer these in Play Console → Policy → Content rating → Start questionnaire:

| Question | Answer |
|----------|--------|
| Category | Utility / Productivity |
| Does the app contain violence? | No |
| Does the app contain sexual content? | No |
| Does the app contain profanity? | No |
| Does the app contain drug references? | No |
| Does the app allow users to interact or exchange information? | No (offline-first, no user-to-user messaging in v1) |
| Does the app share the user's location? | No (location stays on device) |
| Does the app allow purchases? | No |
| Does the app contain ads? | No |
| Is the app directed at children? | No |
| Does the app contain user-generated content? | No (operator enters data locally) |

**Expected rating:** PEGI 3 / Everyone

---

## Step 8 — Data Safety Section

Fill in Play Console → Policy → Data safety:

### Does your app collect or share any user data?
**Yes** — collects data

### Data collected (stored on device only)

| Data type | Collected | Shared | Required | Purpose |
|-----------|-----------|--------|----------|---------|
| Name | Yes | No | Yes | Person profile for missing-person case |
| Photo | Yes | No | No | Person identification |
| Approximate location | Yes | No | Yes | Search zone positioning |
| Precise location | Yes | No | Yes | Last seen location, search map |
| Health info | Yes | No | No | Medical conditions (Alzheimer's, dementia) |
| Other personal info (age, description) | Yes | No | Yes | Person identification |

### Data shared with third parties

| Data type | Shared with | Purpose |
|-----------|-------------|---------|
| Search queries (name, aliases) | Google Custom Search API | Finding public web pages about the missing person |
| Search queries (name, aliases) | Wayback Machine CDX API | Finding archived web profiles |

Note: Only search query text is sent. No profile data, photos, location, or health info is transmitted.

### Security practices

| Question | Answer |
|----------|--------|
| Is data encrypted in transit? | **Yes** (HTTPS with certificate pinning) |
| Is data encrypted at rest? | **Yes** (SQLCipher AES-256, key in Android Keystore) |
| Can users request data deletion? | **Yes** (delete profiles/cases in-app, or uninstall) |
| Does the app follow Google's Families policy? | N/A (not directed at children) |

### Data retention
- Data stored locally until user deletes it or uninstalls the app
- No server-side data retention

---

## Target audience
- **Target age group:** 18 and over
- **App appeals to children?** No
- **Store presence:** General (not Designed for Families)
