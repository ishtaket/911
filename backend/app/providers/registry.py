"""Provider registry — selects real or mock provider based on configured keys."""
from __future__ import annotations

from app.config import Settings, get_settings
from app.providers.archive.commoncrawl import CommonCrawlArchiveProvider
from app.providers.archive.mock import MockArchiveProvider
from app.providers.archive.snippet import SearchSnippetArchiveProvider
from app.providers.archive.wayback import WaybackArchiveProvider
from app.providers.base import (
    ArchiveProvider,
    GeoIntProvider,
    MapsProvider,
    SocialSearchProvider,
    WebSearchProvider,
)
from app.providers.geoint.azure_vision import AzureVisionProvider
from app.providers.geoint.geoseer import GeoSeerProvider
from app.providers.geoint.google_vision import GoogleVisionProvider
from app.providers.geoint.mock import MockGeoIntProvider
from app.providers.geoint.openai_vision import OpenAiVisionReasoner
from app.providers.geoint.picarta import PicartaProvider
from app.providers.maps.google_maps import GoogleMapsProvider
from app.providers.maps.locationiq import LocationIqProvider
from app.providers.maps.mock import MockMapsProvider
from app.providers.maps.osm_nominatim import OsmNominatimProvider
from app.providers.social.facebook import FacebookPublicProvider
from app.providers.social.instagram import InstagramPublicProvider
from app.providers.social.linkedin import LinkedInPublicProvider
from app.providers.social.mock import MockSocialSearchProvider
from app.providers.social.reddit import RedditPublicProvider
from app.providers.social.telegram import TelegramPublicProvider
from app.providers.social.tiktok import TikTokPublicProvider
from app.providers.social.twitter_x import XPublicProvider
from app.providers.social.vk import VkPublicProvider
from app.providers.social.youtube import YouTubePublicProvider
from app.providers.web_search.brave import BraveWebSearchProvider
from app.providers.web_search.google_cse import GoogleCseWebSearchProvider
from app.providers.web_search.mock import MockWebSearchProvider


def get_web_search_providers(settings: Settings | None = None) -> list[WebSearchProvider]:
    """Strict mode: when MOCK_PROVIDERS=false the mock is NOT included as a
    silent fallback. Real providers may raise ProviderNotConfigured / errors
    which the orchestrator audit-logs per provider."""
    s = settings or get_settings()
    if s.mock_providers and not s.brave_search_api_key:
        return [MockWebSearchProvider()]
    real: list[WebSearchProvider] = [
        BraveWebSearchProvider(api_key=s.brave_search_api_key),
        GoogleCseWebSearchProvider(api_key=s.google_maps_api_key),
    ]
    if s.mock_providers:
        real.append(MockWebSearchProvider())
    return real


def get_social_search_providers(settings: Settings | None = None) -> list[SocialSearchProvider]:
    s = settings or get_settings()
    if s.mock_providers:
        return [
            MockSocialSearchProvider(network=n)
            for n in ["facebook", "instagram", "tiktok", "youtube", "telegram", "reddit", "x", "vk", "linkedin"]
        ]
    return [
        FacebookPublicProvider(app_id=s.meta_app_id, app_secret=s.meta_app_secret),
        InstagramPublicProvider(),
        TikTokPublicProvider(),
        YouTubePublicProvider(api_key=s.youtube_api_key),
        TelegramPublicProvider(api_id=s.telegram_api_id, api_hash=s.telegram_api_hash),
        RedditPublicProvider(client_id=s.reddit_client_id, client_secret=s.reddit_client_secret),
        XPublicProvider(),
        VkPublicProvider(),
        LinkedInPublicProvider(),
    ]


def get_archive_providers(settings: Settings | None = None) -> list[ArchiveProvider]:
    s = settings or get_settings()
    if s.mock_providers:
        return [MockArchiveProvider()]
    return [
        WaybackArchiveProvider(),
        CommonCrawlArchiveProvider(),
        SearchSnippetArchiveProvider(),
    ]


def get_geoint_providers(settings: Settings | None = None) -> list[GeoIntProvider]:
    s = settings or get_settings()
    if s.mock_providers and not (s.geoseer_api_key or s.picarta_api_key or s.openai_api_key):
        return [MockGeoIntProvider()]
    return [
        GoogleVisionProvider(credentials_path=s.google_application_credentials),
        AzureVisionProvider(endpoint=s.azure_vision_endpoint, key=s.azure_vision_key),
        GeoSeerProvider(api_key=s.geoseer_api_key),
        PicartaProvider(api_key=s.picarta_api_key),
        OpenAiVisionReasoner(api_key=s.openai_api_key),
    ]


def get_maps_providers(settings: Settings | None = None) -> list[MapsProvider]:
    s = settings or get_settings()
    if s.mock_providers and not (s.google_maps_api_key or s.locationiq_api_key):
        return [MockMapsProvider()]
    return [
        GoogleMapsProvider(api_key=s.google_maps_api_key),
        LocationIqProvider(api_key=s.locationiq_api_key),
        OsmNominatimProvider(),
    ]
