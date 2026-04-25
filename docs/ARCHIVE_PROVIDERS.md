# Archive Providers — Rescue911

Status: real, no key required
Last updated: 2026-04-25

## Summary

Three archive providers are wired and report `state="connected"` /
`auth_type="none"` in `GET /v1/providers`. Operators can dispatch any of
them via `POST /v1/search/archive/start/{case_id}` regardless of
`MOCK_PROVIDERS`. None of them require an API key.

| `provider_id` | Endpoint | Purpose |
|---|---|---|
| `wayback_availability` | `https://archive.org/wayback/available?url=…` | One closest snapshot for a single URL. Cheap, fast, ideal for spot-checks. |
| `wayback_cdx` | `https://web.archive.org/cdx/search/cdx?…` | Multiple snapshots over time for a URL or URL prefix. |
| `common_crawl` | `https://index.commoncrawl.org/collinfo.json` then `…/{id}-index?url=…&output=json` | Captures from the public Common Crawl corpus across the latest 2 indexes. |

All three normalize into the same `ProviderResult` shape with:
- `source_type = "archive"`
- `is_legal_source = True`
- `raw["legal_basis"] = "public_archive"`
- `raw["query_used"] = <url or pattern caller passed>`

## Query strategy

Archive providers expect a **URL or URL prefix**, not a free-text
person name. The current `QueryPlan` builds free-text variants for the
ARCHIVE channel too. Each archive provider therefore short-circuits
free-text input and returns `[]`. To get archive results, the case must
already have a known URL (e.g. a public news article a witness mentioned)
in its evidence; the orchestrator can then re-query the URL via the
archive providers.

A future iteration can extract candidate URLs from existing web/social
evidence and feed them back to archive providers automatically.

## Behavior on failure

Each provider:
- raises `RateLimitError` on HTTP 429 (orchestrator audit-logs it)
- propagates other HTTP errors so the orchestrator records them as
  `provider_error` and continues with the remaining providers
- never silently falls back to mock data

If the entire archive channel returns no results, the orchestrator emits
an `archive_no_results` audit entry (target_id = case id).

## Why no API key

| Service | Auth |
|---|---|
| Internet Archive (Wayback) | None. Rate-limited per IP; respect the public crawler etiquette. |
| Common Crawl | None. Indexes are publicly downloadable. |

Anyone advertising a "Wayback API key" is misinformed — the Internet
Archive does not issue keys for the public Availability or CDX APIs.

## Tests

`backend/app/tests/test_archive_providers.py` covers:
- registry contents in strict + dev mode
- `/v1/providers` archive rows are connected with `auth_type=none`
- Wayback Availability normalization (with `respx`-mocked HTTP)
- Wayback CDX normalization with the explicit `fl=` field order
- Common Crawl: collinfo → per-index lookup → line-delimited JSON parse
- `POST /v1/search/archive/start/{case_id}` returns only ARCHIVE evidence
- free-text queries return `[]` instead of making useless calls
