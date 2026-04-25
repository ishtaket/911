# Probe the Rescue911 backend for liveness.
#
# Default port is 8011 (Rescue911 dedicated local port — 8000 is taken on
# this machine by another service we must not touch).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\dev_backend_health.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\dev_backend_health.ps1 -BaseUrl http://127.0.0.1:8011

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8011"
)

function Probe([string]$path) {
    $url = "$BaseUrl$path"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 3 -UseBasicParsing
        $body = if ($resp.Content.Length -gt 200) { $resp.Content.Substring(0,200) + "..." } else { $resp.Content }
        Write-Host ("[OK ] {0,3}  {1}  {2}" -f $resp.StatusCode, $url, $body.Replace("`r","").Replace("`n"," ").Trim())
        return $true
    } catch {
        Write-Host ("[ERR]      {0}  {1}" -f $url, $_.Exception.Message)
        return $false
    }
}

$ok = $true
$ok = (Probe "/")              -and $ok
$ok = (Probe "/health")        -and $ok
$ok = (Probe "/v1/health")     -and $ok
$ok = (Probe "/v1/cases")      -and $ok
$ok = (Probe "/v1/provider-status") -and $ok
$ok = (Probe "/openapi.json")  -and $ok
# /docs is HTML; just confirm it returns 2xx, don't dump it
$null = Probe "/docs"

if (-not $ok) { exit 1 }
Write-Host "Backend is healthy."
exit 0
