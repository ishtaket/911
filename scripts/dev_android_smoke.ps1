# Smoke test: build → install → launch → capture logcat → check for fatal crash.
$ErrorActionPreference = "Stop"

& "$PSScriptRoot\dev_android_build.ps1"
& "$PSScriptRoot\dev_android_install.ps1"

$adb = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } elseif (Test-Path "C:\platform-tools\adb.exe") { "C:\platform-tools\adb.exe" } else { $null }
if (-not $adb) { Write-Host "[FAIL] ADB not found."; exit 2 }

$pkg = "com.rescue911.osint"
$activity = "$pkg/$pkg.MainActivity"

Write-Host "Clearing logcat..."
& $adb logcat -c

Write-Host "Launching $activity ..."
& $adb shell am start -n $activity | Out-Null
Start-Sleep -Seconds 6

Write-Host "Sampling logcat..."
$out = & $adb logcat -d -t 300 *:E AndroidRuntime:E
Write-Host $out

if ($out -match "FATAL EXCEPTION") {
    Write-Host "[FAIL] FATAL EXCEPTION in logcat."
    exit 4
}
Write-Host "[ OK ] No FATAL EXCEPTION detected in last 300 lines."
