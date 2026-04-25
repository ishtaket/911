"""Provider base interfaces.

Every external API sits behind a typed provider interface with a mock fallback.
If a key is missing, the corresponding mock provider runs and the response
normalizes into the same `ProviderResult` schema.
"""
from __future__ import annotations

from abc import ABC, abstractmethod

from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntResult
from app.schemas.provider_result import ProviderResult


class WebSearchProvider(ABC):
    name: str = "web_search"

    @abstractmethod
    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        ...


class SocialSearchProvider(ABC):
    name: str = "social_search"
    network: str = "generic"

    @abstractmethod
    async def search(self, query: str, language: str = "en", limit: int = 10) -> list[ProviderResult]:
        ...


class ArchiveProvider(ABC):
    name: str = "archive"

    @abstractmethod
    async def lookup(self, url_or_query: str, limit: int = 10) -> list[ProviderResult]:
        ...


class GeoIntProvider(ABC):
    name: str = "geoint"

    @abstractmethod
    async def analyze(self, request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None) -> GeoIntResult:
        ...


class MapsProvider(ABC):
    name: str = "maps"

    @abstractmethod
    async def validate_place(self, lat: float, lon: float, language: str = "en") -> ProviderResult:
        ...

    @abstractmethod
    async def geocode(self, query: str, region: str = "IL", language: str = "en") -> list[ProviderResult]:
        ...
