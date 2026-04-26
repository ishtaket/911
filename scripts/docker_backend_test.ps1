# Run the backend pytest suite inside the Docker image.
# Uses `docker compose run --rm` so a clean ephemeral container is
# spawned (no port collision with a running rescue911-backend service).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_test.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "docker-compose.backend.yml"
if (-not (Test-Path $compose)) {
    Write-Host "[FAIL] $compose not found"
    exit 2
}

Set-Location $root

# `requirements.txt` already includes pytest / pytest-asyncio / respx /
# pytest-cov, so no separate test image is needed.
docker compose -f $compose run --rm --no-deps rescue911-backend `
    python -m pytest app/tests --tb=short -q
$rc = $LASTEXITCODE
if ($rc -ne 0) { Write-Host "[FAIL] pytest exited $rc"; exit $rc }
Write-Host "[OK] backend pytest passed inside Docker."
