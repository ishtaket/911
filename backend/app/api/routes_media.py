"""Media upload (skeleton — stores nothing real yet)."""
from __future__ import annotations

from uuid import UUID, uuid4

from fastapi import APIRouter, File, Form, UploadFile

from app.schemas.audit import AuditEntryCreate
from app.services import audit_service

router = APIRouter()


@router.post("/upload")
async def upload_media(
    case_id: UUID = Form(...),
    file: UploadFile = File(...),
) -> dict:
    media_id = uuid4()
    # TODO: stream to S3/MinIO, compute content hash, run EXIF/OCR pipelines.
    body = await file.read()
    size = len(body)

    audit_service.log(
        AuditEntryCreate(
            action="media.upload",
            target_type="media",
            target_id=str(media_id),
            metadata={"case_id": str(case_id), "filename": file.filename, "size": size},
        )
    )
    return {
        "media_id": str(media_id),
        "case_id": str(case_id),
        "filename": file.filename,
        "size": size,
        "stored": False,  # placeholder until S3 wiring is real
    }
