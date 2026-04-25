# Archive / indexed-deleted-information search

Lawful public archive sources only. **Not** private data recovery, **not** breach data.

## Providers (`backend/app/providers/archive/`)

| File | Class | Notes |
| --- | --- | --- |
| `wayback.py` | `WaybackArchiveProvider` | Wayback CDX index, public, no key |
| `commoncrawl.py` | `CommonCrawlArchiveProvider` | TODO: query CDXJ + parse WARC |
| `snippet.py` | `SearchSnippetArchiveProvider` | TODO: surface snippets from web-search results |
| `mirrors.py` | `PublicMirrorArchiveProvider` | TODO: curated allow-list of public mirrors |
| `mock.py` | `MockArchiveProvider` | Always available |

## Confidence policy

- Archive-only data is *always* lower confidence than live sources.
- Default `confidence ≤ 0.35` for any archive `ProviderResult`.
- Always emit `risk_flags = ["archive_only"]`.
- Require corroboration from at least one **live** source before promoting to `Corroborated` (Level 2).

## Use cases

- Recovering a public profile/page that was deleted but indexed.
- Verifying a claim by finding an older snapshot.
- Surfacing snippets that point at now-dead public URLs.
