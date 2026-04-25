"""Provider interfaces and registry. Every external API sits behind one of these."""
from app.providers.base import (
    ArchiveProvider,
    GeoIntProvider,
    MapsProvider,
    SocialSearchProvider,
    WebSearchProvider,
)

__all__ = [
    "ArchiveProvider",
    "GeoIntProvider",
    "MapsProvider",
    "SocialSearchProvider",
    "WebSearchProvider",
]
