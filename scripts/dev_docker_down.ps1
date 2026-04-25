# Tear down local infra safely.
$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\infra\docker-compose.local.yml"
docker compose -f $composeFile down
