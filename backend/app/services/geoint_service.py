"""GeoINT orchestrator — runs analyze across providers and merges candidates."""
from __future__ import annotations

from app.providers.registry import get_geoint_providers
from app.schemas.audit import AuditEntryCreate
from app.schemas.geoint import GeoIntAnalyzeRequest, GeoIntCandidate, GeoIntResult
from app.services import audit_service
from app.services.hypothesis_service import rank_candidates
from app.services.store import get_store


async def analyze(
    request: GeoIntAnalyzeRequest, image_bytes: bytes | None = None
) -> GeoIntResult:
    providers = get_geoint_providers()
    candidates: list[GeoIntCandidate] = []
    extracted_text: list[str] = []
    landmarks: list[str] = []
    geotag = None

    for p in providers:
        audit_service.log(
            AuditEntryCreate(
                action="provider_call",
                target_type="geoint",
                target_id=str(request.media_id),
                metadata={"provider": p.name, "case_id": str(request.case_id)},
            )
        )
        try:
            res = await p.analyze(request, image_bytes=image_bytes)
            candidates.extend(res.candidates)
            extracted_text.extend(res.extracted_text)
            landmarks.extend(res.detected_landmarks)
            if res.exif_geotag and not geotag:
                geotag = res.exif_geotag
        except Exception as exc:  # noqa: BLE001
            audit_service.log(
                AuditEntryCreate(
                    action="provider_error",
                    target_type="geoint",
                    metadata={"provider": p.name, "error": str(exc)},
                )
            )

    merged = GeoIntResult(
        case_id=request.case_id,
        media_id=request.media_id,
        candidates=rank_candidates(candidates),
        exif_geotag=geotag,
        extracted_text=list(dict.fromkeys(extracted_text)),
        detected_landmarks=list(dict.fromkeys(landmarks)),
    )
    return get_store().add_geoint_result(merged)
