# Rescue911 backend

FastAPI app for the Israel-focused OSINT + GeoINT missing-person platform.

## Run locally

```bash
cd backend
python -m venv .venv
. .venv/Scripts/activate     # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Then:
- `GET http://localhost:8000/` → metadata
- `GET http://localhost:8000/v1/health` → health
- `GET http://localhost:8000/docs` → OpenAPI UI

## Tests

```bash
cd backend
pytest -q
```

## Configuration

All settings come from environment / `.env` (see top-level `.env.example`).
With `MOCK_PROVIDERS=true` and no real API keys, the backend uses safe deterministic
mock providers and is fully functional end-to-end for development.
