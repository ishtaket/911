# Social search

Public posts, public pages, public groups only — through official APIs whenever they exist.

## Networks (`backend/app/providers/social/`)

| Network | File | API source | Status |
| --- | --- | --- | --- |
| Facebook | `facebook.py` | Meta Graph (public Pages/Groups) | mock fallback |
| Instagram | `instagram.py` | Meta Graph (business/creator only) | mock fallback |
| TikTok | `tiktok.py` | Research/Display API | mock fallback |
| YouTube | `youtube.py` | YouTube Data v3 `search.list` | mock fallback |
| Telegram | `telegram.py` | Telethon/MTProto for **public** channels | mock fallback |
| Reddit | `reddit.py` | Official OAuth2 search | mock fallback |
| X (Twitter) | `twitter_x.py` | X API v2 recent search | mock fallback |
| VK | `vk.py` | VK API search | mock fallback |
| LinkedIn | `linkedin.py` | Web-search snippets only | mock fallback |
| Mock | `mock.py` | `MockSocialSearchProvider(network=...)` | always available |

## Hard rules

- **No** scraping behind login walls.
- **No** private profiles, private groups, or DMs.
- **No** automated contacting of any user.
- **No** "fake identity" or unauthenticated session tricks.
- Respect each platform's ToS and rate limits.

## Output

Each provider returns `list[ProviderResult]` with:
- `source_type = SourceType.SOCIAL`
- `provider` — concrete network name
- `is_legal_source = True` (we only run public-only paths)
- `risk_flags` — e.g. `["unverified"]` for fresh / single-source posts
- `content_hash` — for dedup downstream

The orchestrator (`backend/app/services/search_service.py`) fans out across all providers in parallel and audit-logs every call.
