# Docker backend runbook (Windows / Docker Desktop)

The Rescue911 backend is built and run as a single Docker container
described by `docker-compose.backend.yml` at the repo root. Port 8011
is published unchanged so existing host PowerShell calls and the
Android emulator (`http://10.0.2.2:8011`) keep working without any
config changes.

> **Port 8000 is reserved on this host. The backend uses 8011 only.**
> The compose file publishes only `8011:8011`.

---

## 1. Start

```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker_backend_start.ps1
```

What it does:
- Stops any non-Docker process holding port 8011 (e.g., a stale
  `dev_backend_start.ps1` uvicorn) so Docker can take the port.
- `docker compose -f docker-compose.backend.yml up -d --build`.
- Waits up to 60s for `GET /v1/health` to return `status=ok`.
- Exits non-zero if health does not come up.

Endpoints after start:
- Host PowerShell / browser: `http://127.0.0.1:8011`
- Android emulator:          `http://10.0.2.2:8011`

## 2. Stop

```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker_backend_stop.ps1
```

Runs `docker compose down`.

## 3. Logs

```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker_backend_logs.ps1
```

Tails uvicorn stdout/stderr inside the container. Ctrl+C stops the
tail (does NOT stop the container).

## 4. Sanity check

```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker_backend_check.ps1
```

End-to-end check: container running ⇒ `/v1/health` ok ⇒ key
provider rows present in `/v1/providers` ⇒ creates a fresh case ⇒
`POST /v1/search/web/start` ⇒ asserts `vertex_ai_search.state=ok`.
Exits non-zero on any failure with a clear pointer to logs.

## 5. Backend tests inside Docker

```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker_backend_test.ps1
```

Runs `python -m pytest app/tests` inside an ephemeral container
(`docker compose run --rm`). `requirements.txt` already includes
pytest / pytest-asyncio / respx / pytest-cov so no separate test
image is needed. Live Vertex calls are NOT made — provider tests
use respx HTTP mocking.

## 6. Android (no changes required)

- Emulator base URL stays `http://10.0.2.2:8011/`.
- DataSourceBadge in app shows `Data: Backend` once the toggle in
  Settings is on; that toggle is preserved across app restarts.
- No Google credentials ship to Android. Backend remains the sole
  owner of provider keys.

## 7. Required env vars (in `backend/.env` on the host)

These are loaded by compose via `env_file: ./backend/.env` and
exported into the container's process environment. Pydantic Settings
picks them up at startup. The `.env` file is **never** baked into
the image (excluded by `backend/.dockerignore`).

```
MOCK_PROVIDERS=false           # also forced in compose
GOOGLE_CSE_API_KEY=
GOOGLE_CSE_ENGINE_ID=
VERTEX_AI_SEARCH_ENABLED=true
VERTEX_AI_PROJECT_ID=...
VERTEX_AI_LOCATION=global
VERTEX_AI_COLLECTION=default_collection
VERTEX_AI_ENGINE_ID=...
VERTEX_AI_SERVING_CONFIG=default_search
VERTEX_AI_API_KEY=...           # falls back to GOOGLE_CSE_API_KEY
BRAVE_SEARCH_API_KEY=
GOOGLE_KG_API_KEY=
YOUTUBE_API_KEY=
GOOGLE_VISION_API_KEY=
```

## 8. Troubleshooting

| Symptom                                         | Likely cause / fix |
| ---                                             | --- |
| `start.ps1` prints `port 8011 already owned by Docker` | Existing service is still up. Run `docker_backend_stop.ps1` then `start.ps1`, or just `docker compose -f docker-compose.backend.yml restart`. |
| `docker compose: command not found`             | Docker Desktop not running. Start it from the Start menu and wait for the whale icon to settle. |
| `vertex_ai_search.state=unavailable` at dispatch | API key valid but Discovery Engine API not enabled for the GCP project, or key restricted. Check Cloud Console > APIs & Services > Enabled APIs for **Discovery Engine API**. |
| `vertex_ai_search.state=not_configured`         | One of `VERTEX_AI_SEARCH_ENABLED=true`, `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_ENGINE_ID`, `VERTEX_AI_API_KEY` (or `GOOGLE_CSE_API_KEY`) is missing in `backend/.env`. Restart container. |
| Android shows `Data: Mock`                       | Toggle off "Mock mode" in Settings → Backend stays on across restarts. |
| Android dispatch banner: `SocketTimeoutException` | A run took longer than the OkHttp `callTimeout=200s`. Re-tap; archive fan-out is concurrent on backend so subsequent runs are faster. |
| `backend/.env not loaded` (env-driven config defaults wrong) | Compose only reads the file at container *create* time. After editing `backend/.env`, run `docker_backend_stop.ps1` then `start.ps1`. A plain `restart` does NOT re-read the file. |
| Container is `Up` but health is `unhealthy`     | Container reachable, but `/v1/health` returned non-200. `docker_backend_logs.ps1` for uvicorn traceback. |

## What this runbook explicitly does NOT do

- **Does not bring up Postgres / Redis / MinIO.** The current backend
  uses an in-memory store; the multi-service stack lives in
  `infra/docker-compose.local.yml` under the `full` profile and is
  unchanged.
- **Does not touch port 8000.** Only 8011 is published.
- **Does not put any Google credentials in the Docker image.** Keys
  stay on host in `backend/.env` and are injected at run time only.
- **Does not change the Android base URL or any backend public API
  path.** Android UI keeps using `10.0.2.2:8011` exactly as before.
