# Sanity-check the Dockerised Rescue911 backend.
# Exits non-zero on any check failure.
#
# Steps:
#   1. `docker ps` row for rescue911-backend.
#   2. GET /v1/health.
#   3. GET /v1/providers - print state of vertex_ai_search,
#      google_cse, google_cse_site_restricted, wayback_cdx, brave.
#   4. POST /v1/cases (Docker Vertex Sanity).
#   5. POST /v1/search/web/start/{case_id}.
#   6. Print provider summary.
#   7. Confirm vertex_ai_search.state == "ok".
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\docker_backend_check.ps1

[CmdletBinding()]
param([string]$BaseUrl = "http://127.0.0.1:8011")

$ErrorActionPreference = "Stop"

function Fail($msg) { Write-Host "[FAIL] $msg"; exit 1 }
function Ok($msg)   { Write-Host "[OK]  $msg" }

# 1. docker ps row
$ps = docker ps --filter "name=rescue911-backend" --format "{{.Names}}|{{.Status}}|{{.Ports}}"
if (-not $ps) { Fail "rescue911-backend container not running. Did you run docker_backend_start.ps1?" }
Write-Host "container: $ps"

# 2. health
try {
    $h = Invoke-RestMethod -Uri "$BaseUrl/v1/health" -TimeoutSec 5
} catch {
    Fail "GET $BaseUrl/v1/health failed: $($_.Exception.Message)"
}
if ($h.status -ne "ok") { Fail "/v1/health did not return status=ok: $($h | ConvertTo-Json -Compress)" }
Ok ("/v1/health -> status=" + $h.status + " mock_providers=" + $h.mock_providers + " env=" + $h.env)

# 3. /v1/providers
$prov = (Invoke-RestMethod -Uri "$BaseUrl/v1/providers" -TimeoutSec 10).providers
$watchIds = @(
    "vertex_ai_search",
    "google_cse",
    "google_cse_site_restricted",
    "wayback_cdx",
    "brave_web_search"
)
Write-Host ""
Write-Host "key providers:"
foreach ($id in $watchIds) {
    $p = $prov | Where-Object { $_.provider_id -eq $id } | Select-Object -First 1
    if ($p) {
        Write-Host ("  - " + $id.PadRight(30) + " state=" + $p.state.PadRight(20) + " configured=" + $p.configured)
    } else {
        Write-Host ("  - " + $id.PadRight(30) + " <not in registry>")
    }
}

# 4. fresh case
$body = @{
    title = "Docker Vertex Sanity"
    description = "automated sanity check from docker_backend_check.ps1"
    person = @{ full_name = "Docker Vertex Sanity" }
    last_seen_location = "Tel Aviv"
    languages = @("en")
    risk_notes = "automated sanity"
} | ConvertTo-Json -Depth 5
try {
    $case = Invoke-RestMethod -Method POST -Uri "$BaseUrl/v1/cases" `
        -ContentType "application/json" -Body $body -TimeoutSec 5
} catch {
    Fail "POST $BaseUrl/v1/cases failed: $($_.Exception.Message)"
}
$cid = $case.id
Write-Host ""
Ok "fresh case_id=$cid"

# 5. POST web/start
try {
    $r = Invoke-WebRequest -Method POST -Uri "$BaseUrl/v1/search/web/start/$cid" `
        -UseBasicParsing -TimeoutSec 90
} catch {
    Fail "POST $BaseUrl/v1/search/web/start failed: $($_.Exception.Message)"
}
$j = $r.Content | ConvertFrom-Json

Write-Host ""
Write-Host "POST /v1/search/web/start summary:"
Write-Host ("  state=" + $j.state)
Write-Host ("  items_returned=" + $j.items_returned + " items_stored=" + ($j.evidence | Measure-Object).Count)

# 6. provider table from dispatch
Write-Host ""
Write-Host "per-provider:"
$j.providers | ForEach-Object {
    Write-Host ("  - " + $_.provider.PadRight(30) + " state=" + $_.state.PadRight(20) + " items=" + $_.items)
}

# 7. acceptance
$vx = $j.providers | Where-Object { $_.provider -eq "vertex_ai_search" } | Select-Object -First 1
if (-not $vx) { Fail "vertex_ai_search not present in dispatch providers" }
if ($vx.state -ne "ok") {
    Write-Host ""
    Write-Host "[FAIL] vertex_ai_search state=$($vx.state) (expected 'ok')"
    if ($vx.detail) { Write-Host ("       detail: " + $vx.detail) }
    Write-Host "       Verify VERTEX_AI_SEARCH_ENABLED=true and VERTEX_AI_PROJECT_ID/ENGINE_ID/API_KEY are set in backend/.env"
    exit 1
}
if ($vx.items -le 0) {
    Write-Host "[WARN] vertex_ai_search ok but items=0 - query may have no public hits"
}

Write-Host ""
Ok "vertex_ai_search.state=ok items=$($vx.items) - Docker backend is working."
exit 0
