# Tail Rescue911 backend Docker logs (Ctrl+C to stop tailing).
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_logs.ps1

[CmdletBinding()]
param([int]$Tail = 200)

$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "docker-compose.backend.yml"

Set-Location $root
docker compose -f $compose logs -f --tail $Tail rescue911-backend
