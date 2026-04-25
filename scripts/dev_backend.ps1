# Run backend in dev mode (local venv).
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\backend")
Set-Location $root

if (-not (Test-Path ".venv")) {
    Write-Host "Creating venv..."
    python -m venv .venv
}

& ".venv\Scripts\python.exe" -m pip install --upgrade pip | Out-Null
& ".venv\Scripts\python.exe" -m pip install -r requirements.txt | Out-Null

Write-Host "Starting backend on http://0.0.0.0:8000 ..."
& ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
