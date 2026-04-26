"""Provider registry — selects real or mock provider based on configured keys."""
from __future__ import annotations

from app.config import Settings, get_settings
from app.providers.archive.commoncrawl import CommonCrawlArchiveProvider
from app.providers.archive.mock import MockArchiveProvider
from app.providers.archive.snippet import SearchSnippetArchiveProvider
from app.providers.archive.wayback import WaybackArchiveProvider
from app.providers.archive.wayback_availability import WaybackAvailabilityProvider
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
from app.providers.web_search.google_cse_site_restricted import (
    GoogleCseSiteRestrictedProvider,
)
from app.providers.web_search.google_kg import GoogleKnowledgeGraphProvider
from app.providers.web_search.mock import MockWebSearchProvider
from app.providers.web_search.vertex_ai_search import VertexAiSearchProvider


def get_web_search_providers(settings: Settings | None = None) -> list[WebSearchProvider]:
    """Strict mode: when MOCK_PROVIDERS=false the mock is NOT included as a
    silent fallback. Real providers may raise ProviderNotConfigured / errors
    which the orchestrator audit-logs per provider.

    Order matters — providers are queried in list order and surfaced to
    the operator in the same order:
      1. Google CSE Site Restricted JSON API   (RETIRED 2025-01-08;
                                                 surfaces as unavailable)
      2. Google Custom Search JSON API         (closed to new customers;
                                                 may surface unavailable)
      3. Vertex AI Search (`searchLite`)       (Google's official
                                                 migration path; works
                                                 with API key + a
                                                 public-website data
                                                 store)
      4. Google Knowledge Graph                (entity lookup; supplemental)
      5. Brave Search                          (independent index fallback)
      6. SerpAPI                               (TODO; not yet implemented)
      7. Mock                                  (only when MOCK_PROVIDERS=true)

    Knowledge Graph is NOT a general web-search engine — included only
    so the case's person name resolves to public entities (people /
    places / orgs)."""
    s = settings or get_settings()
    has_any_real_key = bool(
        s.google_cse_api_key
        or s.google_kg_api_key
        or s.brave_search_api_key
        or s.vertex_ai_api_key
        or s.vertex_ai_search_enabled
        or s.google_cse_site_restricted_enabled
    )
    if s.mock_providers and not has_any_real_key:
        return [MockWebSearchProvider()]
    # Vertex AI Search may reuse the CSE key — same Google Cloud project.
    vx_key = s.vertex_ai_api_key or s.google_cse_api_key
    real: list[WebSearchProvider] = [
        GoogleCseSiteRestrictedProvider(
            api_key=s.google_cse_api_key,
            cse_id=s.google_cse_engine_id,
            enabled=s.google_cse_site_restricted_enabled,
        ),
        GoogleCseWebSearchProvider(
            api_key=s.google_cse_api_key, cse_id=s.google_cse_engine_id,
        ),
        VertexAiSearchProvider(
            enabled=s.vertex_ai_search_enabled,
            project_id=s.vertex_ai_project_id,
            location=s.vertex_ai_location,
            collection=s.vertex_ai_collection,
            engine_id=s.vertex_ai_engine_id,
            serving_config=s.vertex_ai_serving_config,
            api_key=vx_key,
        ),
        GoogleKnowledgeGraphProvider(api_key=s.google_kg_api_key),
        BraveWebSearchProvider(api_key=s.brave_search_api_key),
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
    """Archive providers need no API keys. Wayback Availability/CDX and
    Common Crawl are always available even in strict mode. Mock is only
    included on top when MOCK_PROVIDERS=true (so dev runs without network
    still see something)."""
    s = settings or get_settings()
    real: list[ArchiveProvider] = [
        WaybackAvailabilityProvider(),
        WaybackArchiveProvider(),
        CommonCrawlArchiveProvider(),
        SearchSnippetArchiveProvider(),
    ]
    if s.mock_providers:
        real.append(MockArchiveProvider())
    return real


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
