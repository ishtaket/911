# Google web-search alternatives — design notes

## Why the JSON API path is closed

`google_cse` provider hit a hard project-level gate at runtime:

```
HTTP 403 PERMISSION_DENIED
"This project does not have the access to Custom Search JSON API."
```

This is **not** an operator-fixable misconfiguration. The Custom Search
JSON API is closed to new customers in many cases, and Google does not
offer an OAuth path around it: the API accepts **only** API-key auth,
and the denial is at the Google Cloud project level. Adding an OAuth
flow on top of a key-only API does nothing.

Therefore:

- The `google_cse` provider stays in the registry (existing operators
  who *do* have access can still use it).
- The specific 403 PERMISSION_DENIED is mapped to a new state,
  `unavailable`, distinct from `not_configured`. Where `not_configured`
  says "set keys", `unavailable` says "request access from the upstream
  vendor or pick a different provider".
- We do **not** introduce OAuth for CSE.
- We do **not** scrape Google HTML SERPs (forbidden by project rules).

## New provider stubs added

### `google_programmable_search_element` — UI-assisted

| field         | value |
|---|---|
| `provider_id` | `google_programmable_search_element` |
| `type`        | `web_search_ui_assisted` |
| `auth_type`   | `none` |
| `state`       | `manual_ui_required` (forced) |

Google's [Programmable Search Element](https://programmablesearchengine.google.com/about/)
is a JS widget that renders Google search results inside a webview /
iframe. The operator runs the search interactively; the app captures
the URLs the operator decides to keep into Evidence. Backend never
queries Google directly — there is no JSON API call, no cookie use, no
scraping. This is the only sanctioned Google web-search path that
works without paying for / being granted JSON API access.

**Implementation outline (deferred):**
- Add an Android Compose screen with `WebView` loading a static HTML
  page that embeds the Programmable Search Element JS (`cse.google.com/cse.js?cx=…`).
- The cx (engine ID) is non-secret and can be embedded in the HTML.
- Operator searches → reviews results → taps "Add to evidence" on a
  hit → backend creates an evidence row with `source_type=web`,
  `provider=google_programmable_search_element`, `legal_basis=public`,
  `url=<chosen URL>`. No private API call ever leaves the device.
- The dispatch endpoint never invokes this provider headlessly; the
  evidence flow is always operator-driven.

### `vertex_ai_search` — site search

| field         | value |
|---|---|
| `provider_id` | `vertex_ai_search` |
| `type`        | `site_search` |
| `auth_type`   | `service_account` |
| `state`       | `not_configured` (until SA + data store wired) |

[Vertex AI Search](https://cloud.google.com/enterprise-search) (formerly
Discovery Engine) indexes a curated list of public sites and serves
ranked results via a Google Cloud project. Distinct from Custom Search
JSON API — separate Google product, separate quotas, **not** subject to
the same PERMISSION_DENIED gate. Authentication is via a backend-only
GCP service account.

**Implementation outline (deferred):**
- Operator builds a public-site allow-list in the backend
  (config-driven; e.g., `news.idf.il`, `israel-rescue.org`, …).
- Backend creates a Vertex AI Search data store seeded from that list.
- Provider issues queries via the Discovery Engine REST API with a
  service-account-signed bearer token (resolved server-side).
- Returns ranked items normalized to `ProviderResult(source_type=WEB,
  provider="vertex_ai_search", legal_basis="public",
  raw={"data_store": …})`.
- Surfaces `connected` when SA JSON path is set + data store ID is
  provided; `not_configured` otherwise; `forbidden` (mapped to
  `unavailable` if Google denies again at project level) on auth
  failure.

## Existing alternatives (already wired)

| Provider | State today | Comment |
|---|---|---|
| `brave_web_search` | `not_configured` | Real Brave Search Web API. Independent index, **not** a Google reseller. Operator-fixable: set `BRAVE_SEARCH_API_KEY`. |
| `google_kg_search` | `connected` (real key set, but currently 403/quota) | Knowledge Graph entity lookup — *not* a web-search engine. Useful for resolving person/place/org names; should not be relied on for general web hits. |
| `serpapi` | `not_configured` | Server-side fetch of public SERPs via SerpAPI (paid). Not yet implemented. Acceptable under project rules because it's an authorized API, not direct scraping by us. |
| `youtube_data_api` | `connected` (currently quota-exhausted) | Social channel only. |

## Recommendation — practical web search **now**

**Use Brave Search Web API as the primary web provider until one of the
Google paths becomes available.** Reasons:

1. Brave Search is a fully separate index from Google — independent
   coverage, no `PERMISSION_DENIED` gating, account onboarding is
   straightforward.
2. We already have a working `BraveWebSearchProvider` (`brave.py`) with
   strict-mode behaviour — only `BRAVE_SEARCH_API_KEY` needs to be set
   in `backend/.env`.
3. Free tier (≈1 req/sec, 2k req/month at time of writing) is plenty
   for OSINT triage; paid tiers exist if scale is needed later.
4. No OAuth needed; backend-only API key; never shipped to Android.

**Action required from operator (one line):**

```
BRAVE_SEARCH_API_KEY=<your-brave-key>   # in backend/.env
```

Then restart the backend. `/v1/providers` will report
`brave_web_search: connected` and Web Search dispatch will produce real
evidence (`provider=brave_web_search`, `source_type=web`, `legal_basis=public`).

**Provider order remains:** `google_cse → google_kg_search → brave_web_search`.
With Brave configured and Google CSE gated, the dispatch state becomes
`completed` (Brave has items) instead of `unavailable` / `not_configured`,
and the per-provider list still surfaces `google_cse=unavailable` so the
operator sees the truth honestly.

## What we explicitly do NOT do

- **No OAuth for Custom Search JSON API.** The API only accepts API-key
  auth; the 403 is project-level; OAuth does not change either.
- **No HTML scraping of `google.com/search?q=…`.** Forbidden by project
  rules and by Google ToS.
- **No cookie-based or signed-in flows** to mimic a real user.
- **No Google credentials shipped to Android.** Backend remains the
  sole owner of all provider config / keys / service-account JSON.
- **No silent mock fallback** when a real provider fails — failures are
  surfaced honestly via the structured per-provider state.
