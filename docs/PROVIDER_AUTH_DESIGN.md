# Provider Auth Design — Rescue911

Status: design / MVP
Owner: backend
Last updated: 2026-04-25

## Non-negotiable boundaries

- **No API keys, OAuth client secrets, provider tokens, cookies, or
  long-lived bearers ever live on Android.** The Android app talks only to
  our backend at `http://10.0.2.2:8011/` (emulator) or the deployed URL.
- All provider integrations are **server-side** (`backend/app/providers/`).
- Public, lawful, permissioned data only. No private-account access, no
  fake identities, no automated contacting, no scraping that violates
  ToS, no stolen/leaked data.
- Operator confirmation (Level 3) remains required to mark anything
  "found / confirmed" — providers only ever produce *evidence candidates*.

## State machine

Every provider, regardless of channel, reports one of these states via
`GET /v1/providers`:

| State | Meaning |
|---|---|
| `disabled` | Operator explicitly turned the provider off. |
| `not_configured` | Backend is missing the API key / service account / tenant id. UI shows the env var name. |
| `auth_required` | OAuth flow not completed yet. UI surfaces a `connect_url` (relative path on backend). |
| `connected` | Configuration present. (Upstream not necessarily pinged — that's `POST /providers/{id}/check`.) |
| `rate_limited` | Last call returned 429 / quota exhausted. |
| `error` | Last call failed; `last_error` carries the cause. |
| `mock` | `MOCK_PROVIDERS=true` in env; safe deterministic stand-in. Never confuses operators (state is explicit). |

## Auth types

| Auth type | Used by | Where the secret lives |
|---|---|---|
| `none` | Wayback CDX, OSM Nominatim, EXIF reader, local OCR | n/a — public/local |
| `api_key` | Brave, Google CSE, YouTube Data v3, GeoSeer, Picarta, Azure Vision, OpenAI Vision, LocationIQ, Google Maps | `.env` on backend host (or secrets manager) |
| `oauth2` | Reddit, Meta (FB/IG), X, VK, TikTok, Instagram, LinkedIn, Google OAuth | client secret in backend `.env`; user tokens stored encrypted server-side once we add a token store. MVP: state stays `auth_required`, no token persisted. |
| `manual_token` | Telegram public (Bot/MTProto credentials) | backend `.env` only |
| `service_account` | Google Vision (GCP service account JSON) | path on backend filesystem; never returned over the wire |

## OAuth flow (when we wire it)

1. Operator opens **Settings → Provider Status** and sees a row with state
   `auth_required` (e.g., Reddit).
2. Operator taps **Open connect page**.
3. Android opens `connect_url` (e.g., `/v1/auth/reddit/start`) in a Custom
   Tab. The backend redirects to the provider's OAuth consent screen.
4. Provider redirects back to `/v1/auth/reddit/callback?code=…`. Backend
   exchanges code for tokens, stores them encrypted server-side, marks
   provider as `connected`.
5. Custom Tab closes; the user returns to the app. The next call to
   `GET /v1/providers` reflects the new state.

**MVP scope** (this commit): step 1–3 only — `connect_url` is exposed,
tap opens the URL, but the backend OAuth start/callback handlers are
stubs that return `auth_required` documentation. No tokens persisted yet.

## Provider-by-provider guidance

### Web Search
- **Brave** — single API key, easy. Recommend as the **first real provider** to wire.
- **Google CSE** — needs a CSE engine ID **and** API key. Engine ID is config; key is the same Google API key family.
- **SerpAPI** — single API key. Paid.

### YouTube
- **Data API v3** — single API key. Public videos and channel metadata only. **Recommend as the first real social provider.**

### Telegram
- Public channel surfaces only. Bot API or MTProto with a registered app (api_id/api_hash). Never request access to private chats.

### Meta (Facebook + Instagram)
- OAuth + App Review. Production needs the `pages_read_engagement` /
  `instagram_basic` scopes and a verified business. MVP: keep at
  `auth_required` until app review is done.

### X (Twitter)
- Official v2 API only. OAuth 2.0 PKCE. Free tier is restrictive.

### Reddit
- OAuth 2.0 (`script` or `web` app). Read-only scopes. Free.

### VK
- Implicit OAuth. Public groups/posts only.

### TikTok / Instagram / LinkedIn
- Display API / Marketing API only. **No personal-profile scraping.**

### Archives
- **Wayback CDX** — public, no auth. Already implemented as
  `wayback_cdx` and reports `connected` always.
- **Common Crawl** — CDXJ index, public, no auth. CDXJ ingestion not yet wired.

### GeoINT
- All keys (Google Vision SA JSON, Azure Vision, GeoSeer, Picarta,
  OpenAI Vision) live in backend env. Pipeline only runs after a media
  upload, which itself is not yet wired (`POST /v1/media`).

## How the Android UI represents states

`feature/providers/ProviderStatusScreen.kt` renders one row per provider:

- Title: `"<display_name> — <state label>"` (e.g., "Brave Search — Not configured")
- Body: `safe_scope_description` + `auth: <auth_type> · type: <provider_type>`
- Trailing: an **Open connect page** button only for `auth_required` providers with a `connect_url`. The button opens the URL in a Custom Tab — Android never sees the secret.
- A WARNING banner is always shown when the backend is in `MOCK_PROVIDERS=true` mode so operators are not misled.

## Why we don't ask the operator to paste keys

The Android app is a field tool. Asking volunteers / analysts to paste API keys is a security and ergonomics dead end:

- Keys would land in DataStore / clipboard / screenshots.
- Operator devices vary in trustworthiness.
- Key rotation becomes per-device.
- The same key across N devices makes per-call audit trails ambiguous.

Backend-only keys give us: rotation in one place, central audit log, rate-limit pooling, secret manager integration (Vault / AWS SM / GCP SM), and a clean compliance story.

## Provider keys at a glance — MVP

| Provider | Key env var | Required? | OAuth needed for MVP? |
|---|---|---|---|
| Brave Search | `BRAVE_SEARCH_API_KEY` | strict mode | No |
| Google CSE | `GOOGLE_MAPS_API_KEY` (CSE engine ID is a separate config) | strict mode | No |
| **Google Knowledge Graph** | `GOOGLE_KG_API_KEY` | strict mode | **No** — entity lookup only |
| **YouTube Data API v3** | `YOUTUBE_API_KEY` | strict mode | **No** — public search only |
| **Google Vision** | `GOOGLE_VISION_API_KEY` *or* `GOOGLE_APPLICATION_CREDENTIALS` (SA JSON) | for media analysis | No — backend-side credentials only |
| Wayback Availability | — | none | No |
| Wayback CDX | — | none | No |
| Common Crawl | — | none | No |
| OSM Nominatim | — | none | No |
| Reddit / Meta / X / VK / TikTok / IG / LinkedIn | OAuth client + secret | optional, post-MVP | Yes (later) |

**Google OAuth is not required for the public-search MVP.** The three
Google API keys above unlock all three Google providers; the OAuth
client id/secret pair (`GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_SECRET`)
stays empty until we add per-user data flows.

See also `docs/ARCHIVE_PROVIDERS.md` for the archive-channel details.

## Next concrete steps

1. **Wire production Brave key** (already real-ready since `615044f`).
2. **Wire production YouTube key** (already real-ready since this commit).
3. **Wire production Google Knowledge Graph key** (already real-ready).
4. **Common Crawl tuning** — currently queries the latest 2 indexes; older
   captures are reachable by listing more.
5. OAuth-flow stubs (`/v1/auth/{provider_id}/{start,callback}`) for the
   social providers above.
6. Token store with envelope encryption (Fernet + per-tenant key).
