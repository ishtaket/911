"""Rich provider registry endpoints (new API surface).

Distinct from the legacy `/v1/provider-status` (still served from
`routes_provider_status.py`). The new shape exposes:
  - state machine: disabled / not_configured / auth_required / connected
                   / rate_limited / error / mock
  - auth_type, configured, requires_user_action, connect_url, last_error
  - safe_scope_description (always visible to operators / auditors)

No secrets are returned. Provider IDs and display names only.
"""
from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException

from app.schemas.provider_status import (
    ProviderCheckResponse,
    ProviderListResponse,
)
from app.services import provider_registry_service

router = APIRouter()


@router.get("", response_model=ProviderListResponse)
def list_providers() -> ProviderListResponse:
    return provider_registry_service.list_providers()


@router.post("/{provider_id}/check", response_model=ProviderCheckResponse)
def check_provider(provider_id: str) -> ProviderCheckResponse:
    """Verifies the provider's *configuration* (not the upstream service).

    A real upstream health-check belongs in the per-provider class once
    it implements its own `check()` against the live API. For now we only
    re-derive state from current Settings, which is honest and cheap.
    """
    info = provider_registry_service.get_provider(provider_id)
    if info is None:
        raise HTTPException(status_code=404, detail="provider not found")
    info.last_checked_at = datetime.now(timezone.utc)
    ok = info.state.value in ("connected", "mock")
    msg = {
        "connected": "Configured. Upstream not pinged in this build.",
        "mock": "Running in mock mode (MOCK_PROVIDERS=true or no real key).",
        "not_configured": f"Missing credential. See .env.example for {provider_id}.",
        "auth_required": "OAuth flow not completed. Open connect_url to start.",
        "disabled": "Provider explicitly disabled.",
        "rate_limited": "Provider is currently rate-limited.",
        "error": "Last call returned an error.",
    }[info.state.value]
    return ProviderCheckResponse(provider=info, ok=ok, message=msg)
