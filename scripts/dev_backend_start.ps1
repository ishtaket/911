# Start the Rescue911 backend (FastAPI / uvicorn) on 0.0.0.0:8000.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\dev_backend_start.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\dev_backend_start.ps1 -Background
#
# Logs:  logs\backend_uvicorn.log

[CmdletBinding()]
param(
    [int]$Port = 8000,
    [switch]$Background
)

$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$logs    = Join-Path $root "logs"
$venvPy  = Join-Path $backend ".venv\Scripts\python.exe"
$logFile = Join-Path $logs "backend_uvicorn.log"

if (-not (Test-Path $backend)) { Write-Host "[FAIL] backend dir missing"; exit 2 }
if (-not (Test-Path $venvPy))  { Write-Host "[FAIL] backend venv missing at $venvPy"; exit 2 }
if (-not (Test-Path $logs))    { New-Item -ItemType Directory -Force -Path $logs | Out-Null }

# stop any older instance bound to the same port (best-effort, no-op if absent)
$existing = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($existing) {
    foreach ($c in $existing) {
        try {
            $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -match 'python|uvicorn') {
                Write-Host "Stopping previous uvicorn (PID $($proc.Id))..."
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                Start-Sleep -Seconds 1
            }
        } catch {}
    }
}

Set-Location $backend
$args = @("-m","uvicorn","app.main:app","--host","0.0.0.0","--port",$Port,"--log-level","info")

if ($Background) {
    Write-Host "Starting backend in background; logs -> $logFile"
    Start-Process -FilePath $venvPy -ArgumentList $args `
        -WindowStyle Hidden `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError  (Join-Path $logs "backend_uvicorn.err.log") `
        -PassThru | Select-Object -Property Id,ProcessName | Format-Table
    Write-Host "Tail with:  Get-Content -Wait -Tail 20 $logFile"
} else {
    Write-Host "Starting backend in foreground (Ctrl+C to stop)..."
    & $venvPy @args
}
