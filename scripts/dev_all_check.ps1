# Run env check + scaffold check + backend tests in one shot.
$ErrorActionPreference = "Continue"

& "$PSScriptRoot\check_local_environment.ps1"
Write-Host ""
& "$PSScriptRoot\verify_project_scaffold.ps1"
Write-Host ""

if (Get-Command python -ErrorAction SilentlyContinue) {
    & "$PSScriptRoot\dev_backend_test.ps1"
} else {
    Write-Host "[WARN] python not on PATH; skipping backend tests."
}
