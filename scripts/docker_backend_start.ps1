# Start the Rescue911 backend in Docker.
#
# Behaviour:
#   1. If a non-Docker process is listening on 8011 (e.g., a stale
#      `dev_backend_start.ps1` uvicorn), stop it so Docker can take
#      the port.
#   2. `docker compose up -d` the rescue911-backend service.
#   3. Wait for the health endpoint to return 200; fail non-zero if
#      it does not within ~60s.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_start.ps1

[CmdletBinding()]
param([int]$Port = 8011)

$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "docker-compose.backend.yml"
if (-not (Test-Path $compose)) {
    Write-Host "[FAIL] docker-compose.backend.yml not found at $compose"
    exit 2
}

Set-Location $root

# --- 1. Stop any non-Docker process holding the port ---
$listeners = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
foreach ($c in $listeners) {
    $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
    if (-not $proc) { continue }
    # Docker's port forwarder runs as `com.docker.backend` on Windows.
    # Don't kill it - it's healthy, port is owned by Docker already.
    if ($proc.ProcessName -match 'com\.docker') {
        Write-Host "[info] port $Port already owned by Docker (PID $($proc.Id) $($proc.ProcessName))."
        continue
    }
    if ($proc.ProcessName -match 'python|uvicorn|node|powershell') {
        Write-Host "[info] stopping non-Docker listener on $Port (PID $($proc.Id) $($proc.ProcessName))..."
        try {
            Stop-Process -Id $proc.Id -Force -ErrorAction Stop
            Start-Sleep -Seconds 1
        } catch {
            Write-Host "[warn] could not stop PID $($proc.Id): $_"
        }
    }
}

# --- 2. up -d the service (build if image missing) ---
Write-Host "Starting rescue911-backend via docker compose..."
docker compose -f $compose up -d --build
$rc = $LASTEXITCODE
if ($rc -ne 0) { Write-Host "[FAIL] docker compose up exited $rc"; exit $rc }

# --- 3. Wait for health (max ~60s) ---
$deadline = (Get-Date).AddSeconds(60)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        $h = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/v1/health" -TimeoutSec 3
        if ($h -and $h.status -eq "ok") { $ok = $true; break }
    } catch {}
    Start-Sleep -Milliseconds 1500
}
if (-not $ok) {
    Write-Host "[FAIL] backend did not respond on http://127.0.0.1:$Port/v1/health within 60s"
    Write-Host "       Inspect logs with: scripts\docker_backend_logs.ps1"
    exit 1
}

Write-Host "[OK] backend up at http://127.0.0.1:$Port"
Write-Host "     Android emulator: http://10.0.2.2:$Port"
Write-Host "     Tail logs:   scripts\docker_backend_logs.ps1"
Write-Host "     Stop:        scripts\docker_backend_stop.ps1"
Write-Host "     Sanity:      scripts\docker_backend_check.ps1"
