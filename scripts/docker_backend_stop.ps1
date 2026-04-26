# Stop the Rescue911 backend Docker service.
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_stop.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "docker-compose.backend.yml"

Set-Location $root
docker compose -f $compose down
$rc = $LASTEXITCODE
if ($rc -ne 0) { Write-Host "[FAIL] docker compose down exited $rc"; exit $rc }
Write-Host "[OK] rescue911-backend stopped."
