# Run backend pytest suite.
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\backend")
Set-Location $root

if (-not (Test-Path ".venv")) {
    python -m venv .venv
    & ".venv\Scripts\python.exe" -m pip install --upgrade pip | Out-Null
    & ".venv\Scripts\python.exe" -m pip install -r requirements.txt | Out-Null
}

& ".venv\Scripts\python.exe" -m pytest -q
