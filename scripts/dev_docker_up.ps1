# Bring up local infra (Postgres+PostGIS, Redis, MinIO).
$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[FAIL] Docker is not on PATH."
    exit 2
}

$server = (& docker info --format "{{.ServerVersion}}" 2>$null)
if (-not $server) {
    Write-Host "[WARN] Docker daemon is not running. Start Docker Desktop first."
    exit 3
}

$composeFile = Join-Path $PSScriptRoot "..\infra\docker-compose.local.yml"
Write-Host "Bringing up base services..."
docker compose -f $composeFile up -d
Write-Host "Done. Use --profile full to also start the backend container."
