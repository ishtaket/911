# Build the Rescue911 backend Docker image.
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_build.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$compose = "docker-compose.backend.yml"
if (-not (Test-Path $compose)) {
    Write-Host "[FAIL] $compose not found in $root"
    exit 2
}

Write-Host "Building rescue911-backend image..."
docker compose -f $compose build
$rc = $LASTEXITCODE
if ($rc -ne 0) { Write-Host "[FAIL] docker compose build exited $rc"; exit $rc }

Write-Host "[OK] image built. Next: scripts\docker_backend_start.ps1"
