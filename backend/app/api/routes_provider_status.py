"""Live status of every external provider, computed from settings + registry."""
from __future__ import annotations

from fastapi import APIRouter

from app.config import get_settings
from app.schemas.provider_status import (
    ProviderInfo,
    ProviderMode,
    ProviderStatusResponse,
)

router = APIRouter()


def _live_or_mock(has_key: bool, force_mock: bool) -> ProviderMode:
    if force_mock or not has_key:
        return ProviderMode.MOCK
    return ProviderMode.LIVE


@router.get("", response_model=ProviderStatusResponse)
def provider_status() -> ProviderStatusResponse:
    s = get_settings()
    mock = s.mock_providers
    providers: list[ProviderInfo] = [
        # web_search
        ProviderInfo(name="Brave Search", category="web_search",
                     mode=_live_or_mock(bool(s.brave_search_api_key), mock),
                     note="Requires BRAVE_SEARCH_API_KEY"),
        ProviderInfo(name="Google Custom Search JSON API", category="web_search",
                     mode=_live_or_mock(bool(s.google_cse_api_key and s.google_cse_engine_id), mock),
                     note="Requires GOOGLE_CSE_API_KEY + GOOGLE_CSE_ENGINE_ID"),
        ProviderInfo(name="SerpAPI", category="web_search",
                     mode=ProviderMode.STUB,
                     note="Interface present, real client TBD"),

        # social
        ProviderInfo(name="Telegram public", category="social",
                     mode=_live_or_mock(bool(s.telegram_api_id and s.telegram_api_hash), mock),
                     note="Requires TELEGRAM_API_ID + TELEGRAM_API_HASH"),
        ProviderInfo(name="Reddit public", category="social",
                     mode=_live_or_mock(bool(s.reddit_client_id and s.reddit_client_secret), mock),
                     note="Requires REDDIT_CLIENT_ID + REDDIT_CLIENT_SECRET"),
        ProviderInfo(name="YouTube Data v3", category="social",
                     mode=_live_or_mock(bool(s.youtube_api_key), mock),
                     note="Requires YOUTUBE_API_KEY"),
        ProviderInfo(name="Facebook public", category="social",
                     mode=_live_or_mock(bool(s.meta_app_id and s.meta_app_secret), mock),
                     note="Requires META_APP_ID + META_APP_SECRET"),
        ProviderInfo(name="Instagram public", category="social", mode=ProviderMode.STUB,
                     note="Public surface only, no automation of private content"),
        ProviderInfo(name="TikTok public", category="social", mode=ProviderMode.STUB),
        ProviderInfo(name="X (Twitter) public", category="social", mode=ProviderMode.STUB),
        ProviderInfo(name="VK public", category="social", mode=ProviderMode.STUB),
        ProviderInfo(name="LinkedIn public", category="social", mode=ProviderMode.STUB),

        # archive
        ProviderInfo(name="Wayback CDX", category="archive", mode=ProviderMode.READY,
                     note="Public, no key required"),
        ProviderInfo(name="Common Crawl", category="archive", mode=ProviderMode.STUB,
                     note="CDXJ index ingestion TBD"),
        ProviderInfo(name="Search snippets", category="archive", mode=ProviderMode.STUB),
        ProviderInfo(name="Public mirrors", category="archive", mode=ProviderMode.STUB),

        # geoint
        ProviderInfo(name="EXIF reader", category="geoint", mode=ProviderMode.READY,
                     note="Local, no key required"),
        ProviderInfo(name="OCR", category="geoint", mode=ProviderMode.STUB,
                     note="Hebrew/Arabic/Cyrillic multi-script TBD"),
        ProviderInfo(name="Google Vision", category="geoint",
                     mode=_live_or_mock(bool(s.google_application_credentials), mock),
                     note="Requires GOOGLE_APPLICATION_CREDENTIALS"),
        ProviderInfo(name="Azure Vision", category="geoint",
                     mode=_live_or_mock(bool(s.azure_vision_endpoint and s.azure_vision_key), mock),
                     note="Requires AZURE_VISION_ENDPOINT + AZURE_VISION_KEY"),
        ProviderInfo(name="GeoSeer", category="geoint",
                     mode=_live_or_mock(bool(s.geoseer_api_key), mock),
                     note="Requires GEOSEER_API_KEY"),
        ProviderInfo(name="Picarta", category="geoint",
                     mode=_live_or_mock(bool(s.picarta_api_key), mock),
                     note="Requires PICARTA_API_KEY"),
        ProviderInfo(name="OpenAI Vision reasoner", category="geoint",
                     mode=_live_or_mock(bool(s.openai_api_key), mock),
                     note="Requires OPENAI_API_KEY"),

        # maps
        ProviderInfo(name="Google Maps / Places", category="maps",
                     mode=_live_or_mock(bool(s.google_maps_api_key), mock),
                     note="Requires GOOGLE_MAPS_API_KEY"),
        ProviderInfo(name="LocationIQ", category="maps",
                     mode=_live_or_mock(bool(s.locationiq_api_key), mock),
                     note="Requires LOCATIONIQ_API_KEY"),
        ProviderInfo(name="OSM Nominatim", category="maps", mode=ProviderMode.READY,
                     note="Public; respect rate limit"),
        ProviderInfo(name="Sentinel Hub", category="maps", mode=ProviderMode.STUB),
    ]
    return ProviderStatusResponse(mock_providers=mock, providers=providers)
