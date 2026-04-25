# Verify required project files exist.
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$required = @(
    "CLAUDE.md",
    ".claude\ROUTER.md",
    ".claude\prompts\MASTER_BUILD_PROMPT.md",
    ".claude\prompts\WHERE_TO_PASTE_PROMPTS.md",
    "docs\PROJECT_SPEC.md",
    "docs\ARCHITECTURE.md",
    "docs\LEGAL_BOUNDARIES.md",
    "docs\PROMPT_MAP.md",
    ".env.example",
    "backend\app\main.py",
    "backend\requirements.txt",
    "android\settings.gradle.kts",
    "android\app\build.gradle.kts",
    "android\app\src\main\AndroidManifest.xml",
    "android\app\src\main\java\com\rescue911\osint\MainActivity.kt",
    "infra\docker-compose.local.yml"
)

$missing = @()
foreach ($f in $required) { if (-not (Test-Path $f)) { $missing += $f } }

$agentCount = (Get-ChildItem ".claude\agents" -Filter "*.md" -ErrorAction SilentlyContinue | Measure-Object).Count
$skillCount = (Get-ChildItem ".claude\skills" -Filter "*.md" -ErrorAction SilentlyContinue | Measure-Object).Count
$screenCount = (Get-ChildItem -Path "android\app\src\main\java\com\rescue911\osint\feature" -Recurse -Filter "*Screen.kt" -ErrorAction SilentlyContinue | Measure-Object).Count
$providerCount = (Get-ChildItem -Path "backend\app\providers" -Recurse -Filter "*.py" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "__*" } | Measure-Object).Count
$testCount = (Get-ChildItem -Path "backend\app\tests" -Filter "test_*.py" -ErrorAction SilentlyContinue | Measure-Object).Count

Write-Host "Required files:"
foreach ($f in $required) {
    if (Test-Path $f) { Write-Host "  [ OK ] $f" } else { Write-Host "  [MISS] $f" }
}
Write-Host ""
Write-Host "Counts:"
Write-Host "  Agents: $agentCount  (expected ≥ 12)"
Write-Host "  Skills: $skillCount  (expected ≥ 10)"
Write-Host "  Android *Screen.kt: $screenCount  (expected ≥ 16)"
Write-Host "  Backend providers (py): $providerCount  (expected ≥ 25)"
Write-Host "  Backend tests: $testCount  (expected ≥ 5)"

$fail = $false
if ($missing.Count -gt 0) { $fail = $true; Write-Host "Missing required files:`n$($missing -join "`n")" }
if ($agentCount -lt 12) { $fail = $true; Write-Host "Too few agents." }
if ($skillCount -lt 10) { $fail = $true; Write-Host "Too few skills." }
if ($screenCount -lt 16) { $fail = $true; Write-Host "Too few Android screens." }

if ($fail) { exit 1 }
Write-Host "[ OK ] Scaffold verification passed."
