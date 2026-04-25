# Install the freshly-built debug APK to a connected device / running emulator.
$ErrorActionPreference = "Stop"

$adb = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } elseif (Test-Path "C:\platform-tools\adb.exe") { "C:\platform-tools\adb.exe" } else { $null }
if (-not $adb) { Write-Host "[FAIL] ADB not found."; exit 2 }

$apk = Resolve-Path -Path (Join-Path $PSScriptRoot "..\android\app\build\outputs\apk\debug\app-debug.apk") -ErrorAction SilentlyContinue
if (-not $apk) {
    Write-Host "[FAIL] APK not found. Run scripts\dev_android_build.ps1 first."
    exit 2
}

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) { Write-Host "[FAIL] No device / emulator connected."; exit 3 }

Write-Host "Installing $apk ..."
& $adb install -r -t $apk.Path
