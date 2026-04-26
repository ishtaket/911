# Google Search Provider Decision — recorded findings & runbook

**Date:** 2026-04-26
**Sources:** Google Cloud / Custom Search official docs (cited inline below).
**Conclusion up front:** **Vertex AI Search `searchLite` is the only Google-backed web/site-search path that can actually work for this project.** All other Google paths are either dead, denied, or non-headless.

## Why OAuth does not fix Google Custom Search JSON API

The runtime error reported by the operator was:

```
HTTP 403 PERMISSION_DENIED
"This project does not have the access to Custom Search JSON API."
```

Three independent reasons OAuth cannot fix this:

1. **Custom Search JSON API only accepts API-key auth.** The endpoint
   `https://www.googleapis.com/customsearch/v1` does not have an OAuth
   path documented for general web search. Adding an OAuth flow
   produces nothing the API will honour.
2. **The denial is at the Google Cloud project level**, not the user
   level. OAuth changes the *user* identity calling the API; it does
   not grant the *project* permission to enable the API. A signed-in
   user from a project that lacks API access still gets PERMISSION_DENIED.
3. **Google has closed Custom Search JSON API to new customers.** The
   official overview page states: *"The Custom Search JSON API is
   closed to new customers. Existing customers have until January 1,
   2027 to transition to alternatives."* Even if OAuth were accepted,
   a new project cannot enable the API.

## Why Site Restricted JSON API is not a viable workaround

Per the official documentation
(<https://developers.google.com/custom-search/v1/site_restricted_api>):

> "The Custom Search Site Restricted JSON API endpoints will cease to
>  serve traffic on January 8, 2025."
>
> "migrate to Google Cloud's Vertex AI Search."

Today is **2026-04-26** — the Site Restricted endpoint has been dead
for 15+ months. Calling it would only generate a network error
indistinguishable from transient failures. The provider therefore
**raises `ProviderUnavailable` immediately without making the call**,
unless the explicit env flag `GOOGLE_CSE_SITE_RESTRICTED_ENABLED=true`
is set (in case Google has restored access for a specific project,
which we do not assume).

Engine-configuration reminders, kept here for completeness in case the
endpoint ever comes back:

- The Programmable Search Engine must search **10 or fewer sites**.
- "Search the entire web" must be **OFF**.
- No global TLD patterns.

## Vertex AI Search — the path forward

Vertex AI Search (a.k.a. Discovery Engine / "Agent Search") is the
**official Google migration path** for the retired Site Restricted
JSON API. It exposes a `searchLite` method that:

- accepts API-key authentication (no service account, no OAuth),
- is restricted to **public-website data stores** (exactly the
  workload Site Restricted JSON API used to handle), and
- runs against the discoveryengine.googleapis.com endpoint
  (separate Google product from Custom Search JSON API; **not
  subject to the same PERMISSION_DENIED gate**).

REST endpoint:

```
POST https://discoveryengine.googleapis.com/v1/
  projects/{PROJECT_ID}
  /locations/{LOCATION}                 (e.g. "global")
  /collections/{COLLECTION}             (default: "default_collection")
  /engines/{ENGINE_ID}
  /servingConfigs/{SERVING_CONFIG_ID}   (default: "default_search")
  :searchLite?key={API_KEY}
```

Body: `{"query": "...", "pageSize": N}`.

Required Console steps to wire it up:

1. **Cloud Console → AI Applications → Data Stores → Create data store**.
   Source = **Website Content**. Add up to N public-site URLs (one per
   line) under "Sites to include". Decide whether to enable Advanced
   website indexing (off → no domain verification needed; basic search
   only).
2. **Cloud Console → AI Applications → Apps → Create app**. App type
   = Search. Bind it to the data store created in step 1. Note the
   **Engine ID** that appears.
3. **Cloud Console → APIs & Services → Credentials → Create API key**.
   Recommended HTTP-referer or API restriction:
   `https://discoveryengine.googleapis.com/*`. The existing
   `GOOGLE_CSE_API_KEY` may also work — `searchLite` and the existing
   key share the Cloud project.
4. Set the env vars below; restart the backend.

Env vars (added to `.env.example`):

```
VERTEX_AI_SEARCH_ENABLED=true
VERTEX_AI_PROJECT_ID=<gcp-project-id>
VERTEX_AI_LOCATION=global              # or us / eu
VERTEX_AI_COLLECTION=default_collection
VERTEX_AI_ENGINE_ID=<engine-id-from-console>
VERTEX_AI_SERVING_CONFIG=default_search
VERTEX_AI_API_KEY=                      # falls back to GOOGLE_CSE_API_KEY
```

After restart:
- `/v1/providers` reports `vertex_ai_search: connected`.
- `POST /v1/search/web/start/{cid}` runs the searchLite query and
  produces `provider=vertex_ai_search` evidence with
  `source_type=web`, `legal_basis=public`, and Vertex's per-result
  `displayLink`, `link`, `title`, `snippet`, plus `vertex_id` for
  audit. Confidence is set to `0.62`.

## Provider order (web channel)

```
1. google_cse_site_restricted    (retired 2025-01-08 → unavailable)
2. google_cse                    (closed to new customers / project denied)
3. vertex_ai_search              (Google's official replacement)
4. google_kg_search              (entity lookup; supplemental, not web search)
5. brave_web_search              (independent index fallback)
6. mock_web_search               (only when MOCK_PROVIDERS=true)
```

The deprecated and denied entries appear FIRST so their states are
prominent in the operator UI — the operator immediately sees that
Google's two JSON paths are gone and the path forward is `vertex_ai_search`
(or Brave as an immediate fallback).

## Compliance / hard-rule re-statement

We do NOT:
- add OAuth for Custom Search JSON API,
- add OAuth for Site Restricted JSON API,
- scrape `google.com/search?q=…` HTML,
- use cookies, signed-in mimicry, or any unofficial Google scraping,
- ship Google credentials to Android.

Backend remains the single owner of all provider keys / service-account
JSON. Android only displays provider status that the backend reports.

## Recommended next step (operator action)

1. **Wire Vertex AI Search** — follow the 4-step Console runbook
   above. Total time ≈ 10 minutes. Once configured, this is real
   Google-backed search working for this project.
2. **In parallel, set `BRAVE_SEARCH_API_KEY`** as a non-Google fallback
   so the channel stays healthy if Google quota / billing issues come
   back. Brave is independent of Google.
