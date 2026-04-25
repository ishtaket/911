# API keys

All API keys are stored on the **backend only**, never on Android, never in the repo.

## Where keys live

- Local dev: `.env` next to `backend/` (git-ignored). Use `.env.example` as the template.
- Staging: `.env.staging` outside the repo, mounted via `env_file:` in compose.
- Production: a vault (AWS Secrets Manager / GCP Secret Manager / HashiCorp Vault). Backend reads via env at startup.

## Key catalog

| Provider | Env var(s) | Used by |
| --- | --- | --- |
| Brave Search | `BRAVE_SEARCH_API_KEY` | `BraveWebSearchProvider` |
| Google Maps | `GOOGLE_MAPS_API_KEY` | `GoogleMapsProvider` |
| Google Vision (service account JSON) | `GOOGLE_APPLICATION_CREDENTIALS` | `GoogleVisionProvider` |
| LocationIQ | `LOCATIONIQ_API_KEY` | `LocationIqProvider` |
| Azure Vision | `AZURE_VISION_ENDPOINT`, `AZURE_VISION_KEY` | `AzureVisionProvider` |
| GeoSeer | `GEOSEER_API_KEY` | `GeoSeerProvider` |
| Picarta | `PICARTA_API_KEY` | `PicartaProvider` |
| OpenAI | `OPENAI_API_KEY` | `OpenAiVisionReasoner` |
| YouTube Data | `YOUTUBE_API_KEY` | `YouTubePublicProvider` |
| Telegram MTProto | `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` | `TelegramPublicProvider` |
| Meta (FB/IG public) | `META_APP_ID`, `META_APP_SECRET` | `FacebookPublicProvider` |
| Reddit | `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET` | `RedditPublicProvider` |

## Mock fallback

If the relevant key is empty (or `MOCK_PROVIDERS=true`), the corresponding mock provider runs and returns deterministic, normalized `ProviderResult` objects. The system is fully functional without any real keys.

## Key rotation

- Rotate every 90 days minimum, immediately on suspected compromise.
- Audit log records every `provider_call`; investigate anomalous spikes per provider.
