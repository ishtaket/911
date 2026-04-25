$ErrorActionPreference = "Stop"

Write-Host "Checking Claude Code project scaffold..."

$requiredFiles = @(
  "CLAUDE.md",
  ".claude\ROUTER.md",
  ".claude\prompts\MASTER_BUILD_PROMPT.md",
  ".claude\prompts\WHERE_TO_PASTE_PROMPTS.md",
  "docs\PROJECT_SPEC.md",
  "docs\ARCHITECTURE.md",
  "docs\LEGAL_BOUNDARIES.md",
  "docs\PROMPT_MAP.md",
  ".env.example"
)

foreach ($file in $requiredFiles) {
  if (!(Test-Path $file)) {
    throw "Missing: $file"
  }
}

$agentCount = (Get-ChildItem ".claude\agents" -Filter "*.md" | Measure-Object).Count
$skillCount = (Get-ChildItem ".claude\skills" -Filter "*.md" | Measure-Object).Count

Write-Host "Agents: $agentCount"
Write-Host "Skills: $skillCount"

if ($agentCount -lt 12) {
  throw "Expected at least 12 agents"
}

if ($skillCount -lt 10) {
  throw "Expected at least 10 skills"
}

Write-Host "Claude Code scaffold OK."
