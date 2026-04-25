# GeoINT pipeline

```
Android upload  →  Backend stores media (S3/MinIO)
                       ↓
                EXIF parser (GPS / time / device)
                       ↓
                OCR (en / he / ru / ar)
                       ↓
       Vision providers (Google / Azure / GeoSeer / Picarta)
                       ↓
                OpenAI Vision reasoner (over previous outputs)
                       ↓
       Maps/POI validation (Google / LocationIQ / OSM Nominatim)
                       ↓
       Hypothesis engine ranks candidates
                       ↓
       Three-level validation (L1 → L2 → L3)
```

## Provider classes

Path: `backend/app/providers/geoint/`

| File | Class | Notes |
| --- | --- | --- |
| `mock.py` | `MockGeoIntProvider` | Returns Tel Aviv / Jerusalem / Haifa candidates |
| `exif.py` | `ExifProvider` | TODO: real EXIF (Pillow / piexif) |
| `ocr.py` | `OcrProvider` | TODO: multi-script OCR |
| `google_vision.py` | `GoogleVisionProvider` | Falls back to mock when no creds |
| `azure_vision.py` | `AzureVisionProvider` | Falls back to mock when no key |
| `geoseer.py` | `GeoSeerProvider` | Bias `region_hint=IL` |
| `picarta.py` | `PicartaProvider` | Bias to IL, respect rate limits |
| `openai_vision.py` | `OpenAiVisionReasoner` | Reasoner — not single-source-of-truth |

## Output contract

Every GeoINT call returns `GeoIntResult`:

```python
class GeoIntResult(BaseModel):
    case_id: UUID
    media_id: UUID
    candidates: list[GeoIntCandidate]   # ranked by confidence
    exif_geotag: GeoTag | None
    extracted_text: list[str]
    detected_landmarks: list[str]
    finished_at: datetime
```

Each `GeoIntCandidate`:

```python
class GeoIntCandidate(BaseModel):
    lat: float
    lon: float
    place_name: str | None
    confidence: float       # 0..1
    evidence: list[str]     # human-readable chips for the UI
    contradictions: list[str]
    risk: str | None
    next_checks: list[str]
    sources: list[str]      # provider names that contributed
```

## Rules

- Never return one GPS coordinate as fact from one provider.
- A single archive-only or single-vision finding cannot exceed `confidence ≤ 0.45`.
- Maps/POI validation must run before any candidate is shown as Corroborated.
- Final "Confirmed" requires Level 3 human action — never automatic.
