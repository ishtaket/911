# Rescue911 backend

FastAPI app for the Israel-focused OSINT + GeoINT missing-person platform.

## Run locally

```bash
cd backend
python -m venv .venv
. .venv/Scripts/activate     # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 127.0.0.1 --port 8011
```

> Rescue911 uses port **8011** as its dedicated local-dev port. Port 8000 is
> reserved on this machine by another service we must not touch. The Android
> emulator reaches the host backend via `http://10.0.2.2:8011/`.

Then:
- `GET http://localhost:8011/` → metadata
- `GET http://localhost:8011/v1/health` → health
- `GET http://localhost:8011/docs` → OpenAPI UI

## Tests

```bash
cd backend
pytest -q
```

## Configuration

All settings come from environment / `.env` (see top-level `.env.example`).
With `MOCK_PROVIDERS=true` and no real API keys, the backend uses safe deterministic
mock providers and is fully functional end-to-end for development.
