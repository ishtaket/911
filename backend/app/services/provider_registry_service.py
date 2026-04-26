"""Build the rich provider list from current Settings.

Single source of truth for `GET /v1/providers` and
`POST /v1/providers/{id}/check`.

Rules:
- never return secrets, only state metadata
- if `MOCK_PROVIDERS=true` AND no key for a provider → state=mock
- if `MOCK_PROVIDERS=false` AND no key   → state=not_configured (or auth_required for OAuth)
- if `MOCK_PROVIDERS=false` AND key set  → state=connected (we don't ping the
  upstream here; that happens in `check()` once a real client lands)
- public-only providers (Wayback, OSM Nominatim, EXIF) without key needs
  → state=connected, auth=none
"""
from __future__ import annotations

from datetime import datetime, timezone

from app.config import Settings, get_settings
from app.schemas.provider_status import (
    ProviderAuthType,
    ProviderInfoV2,
    ProviderListResponse,
    ProviderState,
    ProviderType,
)


def _state(has_key: bool, mock: bool, auth_type: ProviderAuthType) -> ProviderState:
    if has_key:
        return ProviderState.CONNECTED
    if mock:
        return ProviderState.MOCK
    return (
        ProviderState.AUTH_REQUIRED
        if auth_type == ProviderAuthType.OAUTH2
        else ProviderState.NOT_CONFIGURED
    )


def _row(
    provider_id: str,
    type: ProviderType,
    display_name: str,
    *,
    has_key: bool,
    mock: bool,
    auth_type: ProviderAuthType,
    safe_scope: str,
    note: str | None = None,
    connect_url: str | None = None,
    public_no_auth: bool = False,
    forced_state: ProviderState | None = None,
) -> ProviderInfoV2:
    if forced_state is not None:
        st = forced_state
    elif public_no_auth:
        st = ProviderState.CONNECTED
    else:
        st = _state(has_key, mock, auth_type)
    return ProviderInfoV2(
        provider_id=provider_id,
        type=type,
        display_name=display_name,
        state=st,
        auth_type=auth_type if not public_no_auth else ProviderAuthType.NONE,
        configured=has_key or public_no_auth,
        requires_user_action=st in (ProviderState.AUTH_REQUIRED, ProviderState.NOT_CONFIGURED),
        connect_url=connect_url if st == ProviderState.AUTH_REQUIRED else None,
        safe_scope_description=safe_scope,
        last_checked_at=datetime.now(timezone.utc),
        last_error=None,
        note=note,
    )


def list_providers(settings: Settings | None = None) -> ProviderListResponse:
    s = settings or get_settings()
    mock = s.mock_providers

    rows: list[ProviderInfoV2] = [
        # ---------- web search ----------
        _row("brave_web_search", ProviderType.WEB_SEARCH, "Brave Search",
             has_key=bool(s.brave_search_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Public web index. No private data."),
        _row("google_cse_site_restricted",
             ProviderType.SITE_SEARCH,
             "Google CSE Site Restricted JSON API (retired 2025-01-08)",
             has_key=bool(s.google_cse_api_key and s.google_cse_engine_id),
             mock=False,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope=(
                 "Official Google Site Restricted Custom Search JSON API "
                 "over <=10 configured public sites. Endpoint retired by "
                 "Google on 2025-01-08; migrate to Vertex AI Search."
             ),
             note=(
                 "Endpoint deprecated by Google "
                 "(developers.google.com/custom-search/v1/site_restricted_api). "
                 "Provider raises ProviderUnavailable unless "
                 "GOOGLE_CSE_SITE_RESTRICTED_ENABLED=true and Google has "
                 "restored access for your Cloud project. Use "
                 "vertex_ai_search instead."
             ),
             forced_state=(
                 ProviderState.UNAVAILABLE
                 if not s.google_cse_site_restricted_enabled
                 else None
             )),
        _row("google_cse", ProviderType.WEB_SEARCH, "Google Custom Search JSON API",
             has_key=bool(s.google_cse_api_key and s.google_cse_engine_id), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope=(
                 "Official Google Programmable Search / Custom Search JSON "
                 "API. Public web results only. Backend-side key only."
             ),
             note=(
                 "Set GOOGLE_CSE_API_KEY and GOOGLE_CSE_ENGINE_ID. If the "
                 "Custom Search JSON API is closed to this Google Cloud "
                 "project, dispatch surfaces state=unavailable with a 403 "
                 "PERMISSION_DENIED detail. OAuth does NOT fix that — the "
                 "API only accepts API-key auth and the denial is at the "
                 "project level."
             )),
        _row("google_programmable_search_element",
             ProviderType.WEB_SEARCH_UI_ASSISTED,
             "Google Programmable Search Element (UI-assisted)",
             has_key=False, mock=False,
             auth_type=ProviderAuthType.NONE,
             safe_scope=(
                 "Google's official Programmable Search rendered as a JS "
                 "search element inside a webview / browser. Operator "
                 "browses results in the embedded surface and copies "
                 "promising hits into evidence; backend never queries "
                 "Google directly. No JSON API, no scraping, no cookies."
             ),
             note=(
                 "Manual UI flow only — backend will not invoke this "
                 "provider headlessly. Recommended fallback when Custom "
                 "Search JSON API is unavailable for the Google project."
             ),
             forced_state=ProviderState.MANUAL_UI_REQUIRED),
        _row("vertex_ai_search",
             ProviderType.SITE_SEARCH,
             "Vertex AI Search (searchLite, public website data store)",
             has_key=bool(
                 s.vertex_ai_search_enabled
                 and s.vertex_ai_project_id
                 and s.vertex_ai_engine_id
                 and (s.vertex_ai_api_key or s.google_cse_api_key)
             ),
             mock=False,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope=(
                 "Google Cloud Vertex AI Search (Discovery Engine) over "
                 "an operator-curated public-website data store. Uses "
                 "the searchLite API-key path — no service account "
                 "needed for public-site search. Backend-side key only; "
                 "never ships to Android."
             ),
             note=(
                 "Official migration path for the retired Custom Search "
                 "Site Restricted JSON API "
                 "(cloud.google.com/.../migrate-from-cse). Requires: "
                 "VERTEX_AI_SEARCH_ENABLED=true, VERTEX_AI_PROJECT_ID, "
                 "VERTEX_AI_ENGINE_ID, and an API key (VERTEX_AI_API_KEY "
                 "or fallback to GOOGLE_CSE_API_KEY). See "
                 "docs/GOOGLE_SEARCH_PROVIDER_DECISION.md."
             )),
        _row("serpapi", ProviderType.WEB_SEARCH, "SerpAPI",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Server-side scrape of Google SERPs. Public results only."),

        # ---------- public social ----------
        _row("youtube_data_api", ProviderType.YOUTUBE, "YouTube Data API v3",
             has_key=bool(s.youtube_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Public YouTube search only. No private account access."),
        _row("telegram_public", ProviderType.TELEGRAM_PUBLIC, "Telegram public channels",
             has_key=bool(s.telegram_api_id and s.telegram_api_hash), mock=mock,
             auth_type=ProviderAuthType.MANUAL_TOKEN,
             safe_scope="Public channels and messages only. No private chats, no impersonation."),
        _row("reddit_public", ProviderType.REDDIT_PUBLIC, "Reddit public",
             has_key=bool(s.reddit_client_id and s.reddit_client_secret), mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public subreddits and posts via official API.",
             connect_url="/v1/auth/reddit/start"),
        _row("meta_public_pages", ProviderType.META_PUBLIC_PAGES, "Meta (FB/IG) public pages",
             has_key=bool(s.meta_app_id and s.meta_app_secret), mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public Pages and permissioned business profiles only. App-review required for production.",
             connect_url="/v1/auth/meta/start"),
        _row("x_public", ProviderType.X_PUBLIC, "X (Twitter) public",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public tweets via official API only. No scraping.",
             connect_url="/v1/auth/x/start"),
        _row("vk_public", ProviderType.VK_PUBLIC, "VK public",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public profiles and groups via official API."),
        _row("tiktok_public", ProviderType.PUBLIC_SOCIAL, "TikTok public",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public videos via official Display API."),
        _row("instagram_public", ProviderType.PUBLIC_SOCIAL, "Instagram public",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public business profiles only. Personal accounts excluded."),
        _row("linkedin_public", ProviderType.PUBLIC_SOCIAL, "LinkedIn public",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.OAUTH2,
             safe_scope="Public organization pages via official Marketing API."),

        # ---------- archive (all public, no API key) ----------
        _row("wayback_availability", ProviderType.ARCHIVE, "Wayback Availability",
             has_key=False, mock=False, public_no_auth=True,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Public archive metadata only. No private, paywalled, or account-protected access."),
        _row("wayback_cdx", ProviderType.ARCHIVE, "Wayback Machine (CDX)",
             has_key=False, mock=False, public_no_auth=True,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Public archive metadata only. No private, paywalled, or account-protected access."),
        _row("common_crawl", ProviderType.ARCHIVE, "Common Crawl (CDXJ)",
             has_key=False, mock=False, public_no_auth=True,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Public archive metadata only. No private, paywalled, or account-protected access."),

        # ---------- geoint / vision ----------
        _row("exif_reader", ProviderType.GEOINT, "EXIF reader",
             has_key=False, mock=False, public_no_auth=True,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Local-only metadata extraction from uploaded media."),
        _row("ocr_tesseract", ProviderType.VISION_OCR, "OCR (Tesseract / multi-script)",
             has_key=False, mock=mock,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Local OCR. Hebrew/Arabic/Cyrillic models not yet bundled."),
        _row("google_vision", ProviderType.VISION_OCR, "Google Vision",
             has_key=bool(s.google_vision_api_key or s.google_application_credentials),
             mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Image OCR/labels/landmark analysis on uploaded media only. Backend-side credentials only."),
        _row("google_kg_search", ProviderType.WEB_SEARCH, "Google Knowledge Graph",
             has_key=bool(s.google_kg_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Read-only public entity lookup for people, places, and organizations. Not a web search engine."),
        _row("azure_vision", ProviderType.VISION_OCR, "Azure Vision",
             has_key=bool(s.azure_vision_endpoint and s.azure_vision_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Vision/OCR on operator-uploaded media."),
        _row("geoseer", ProviderType.GEOINT, "GeoSeer",
             has_key=bool(s.geoseer_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Image-to-location predictions on uploaded media."),
        _row("picarta", ProviderType.GEOINT, "Picarta",
             has_key=bool(s.picarta_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Image-to-location predictions on uploaded media."),
        _row("openai_vision", ProviderType.VISION_OCR, "OpenAI Vision reasoner",
             has_key=bool(s.openai_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="LLM-assisted reasoning over operator-uploaded media."),

        # ---------- maps / geocode ----------
        _row("google_maps", ProviderType.MAPS_GEOCODE, "Google Maps / Places",
             has_key=bool(s.google_maps_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Public POI / geocoding."),
        _row("locationiq", ProviderType.MAPS_GEOCODE, "LocationIQ",
             has_key=bool(s.locationiq_api_key), mock=mock,
             auth_type=ProviderAuthType.API_KEY,
             safe_scope="Public POI / geocoding."),
        _row("osm_nominatim", ProviderType.MAPS_GEOCODE, "OpenStreetMap Nominatim",
             has_key=False, mock=False, public_no_auth=True,
             auth_type=ProviderAuthType.NONE,
             safe_scope="Public OSM geocoder. Respect rate limits."),
    ]

    return ProviderListResponse(mock_providers=mock, providers=rows)


def get_provider(provider_id: str, settings: Settings | None = None) -> ProviderInfoV2 | None:
    for p in list_providers(settings).providers:
        if p.provider_id == provider_id:
            return p
    return None
