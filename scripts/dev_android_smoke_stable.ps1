# Stable Rescue911 smoke: boot AVD with hardened flags, install APK,
# launch, capture, and ONLY fail when the failure is in our app.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1 -Avd Rescue911_API_30_default
#
# System ANRs (system_server / com.android.systemui / Google apps) are
# treated as emulator warnings. Only ANR / FATAL EXCEPTION / Unable to
# start activity records that name com.rescue911.osint fail the run.

[CmdletBinding()]
param(
    [string]$Avd = "Rescue911_API_30_default",
    [string]$Pkg = "com.rescue911.osint",
    [int]$BootTimeoutSec = 240,
    [int]$SettleSec = 30
)

$ErrorActionPreference = "Stop"

$root  = Split-Path -Parent $PSScriptRoot
$logs  = Join-Path $root "logs"
$apk   = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Force -Path $logs | Out-Null }
if (-not (Test-Path $apk))  { Write-Host "[FAIL] APK not found at $apk -- run scripts\dev_android_build.ps1 first."; exit 2 }

# ---- toolchain
$jdk = "C:\Program Files\Java\jdk-19"
if (Test-Path (Join-Path $jdk "bin\javac.exe")) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
} else {
    Write-Host "[WARN] JDK 19 not found at $jdk; relying on caller's JAVA_HOME."
}

$sdk = "C:\Android\Sdk"
if (-not (Test-Path $sdk)) { Write-Host "[FAIL] Android SDK not found at $sdk."; exit 2 }
$env:ANDROID_HOME     = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$adb       = Join-Path $sdk "platform-tools\adb.exe"
$emulator  = Join-Path $sdk "emulator\emulator.exe"

if (-not (Test-Path $adb))      { Write-Host "[FAIL] adb.exe missing under $sdk\platform-tools."; exit 2 }
if (-not (Test-Path $emulator)) { Write-Host "[FAIL] emulator.exe missing under $sdk\emulator."; exit 2 }

# ---- pick a target: existing connected device, else boot the requested AVD
function Get-OnlineDevice {
    & $adb devices | Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] } |
        Select-Object -First 1
}

$serial = Get-OnlineDevice
if (-not $serial) {
    $known = & $emulator -list-avds
    if ($known -notcontains $Avd) {
        Write-Host "[FAIL] AVD '$Avd' not found. Known AVDs:"
        $known | ForEach-Object { Write-Host "  - $_" }
        exit 3
    }

    Write-Host "Booting AVD '$Avd' with stable flags..."
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $Avd,
        "-no-snapshot-load", "-no-snapshot-save",
        "-no-boot-anim", "-no-audio",
        "-memory", "3072", "-cores", "2",
        "-gpu", "swiftshader_indirect",
        "-netdelay", "none", "-netspeed", "full"
    ) -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $logs "emulator_stable_stdout.log") `
      -RedirectStandardError  (Join-Path $logs "emulator_stable_stderr.log") | Out-Null

    & $adb wait-for-device | Out-Null
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    do {
        Start-Sleep 2
        $bc = (& $adb shell getprop sys.boot_completed 2>$null)
        if ($bc) { $bc = $bc.Trim() }
    } until ($bc -eq "1" -or (Get-Date) -gt $deadline)

    if ($bc -ne "1") { Write-Host "[FAIL] Boot did not complete within $BootTimeoutSec s."; exit 4 }
    $serial = Get-OnlineDevice
    Write-Host "Booted: $serial"
}

# ---- ride out the boot stampede a bit
Write-Host "Settling $SettleSec s for the system to calm down..."
Start-Sleep -Seconds $SettleSec

# ---- developer tweaks
& $adb -s $serial shell settings put global window_animation_scale 0     | Out-Null
& $adb -s $serial shell settings put global transition_animation_scale 0 | Out-Null
& $adb -s $serial shell settings put global animator_duration_scale 0    | Out-Null

# ---- install + launch
Write-Host "Installing APK..."
$installOut = & $adb -s $serial install -r $apk 2>&1
Write-Host $installOut
if ($LASTEXITCODE -ne 0 -or ($installOut -join "`n") -notmatch "Success") {
    if ($installOut -match "INSTALL_FAILED_(VERSION_DOWNGRADE|UPDATE_INCOMPATIBLE)") {
        Write-Host "Install conflict -- uninstalling $Pkg and retrying..."
        & $adb -s $serial uninstall $Pkg | Out-Null
        $installOut = & $adb -s $serial install -r $apk 2>&1
        Write-Host $installOut
        if (($installOut -join "`n") -notmatch "Success") { Write-Host "[FAIL] Install failed."; exit 5 }
    } else {
        Write-Host "[FAIL] Install failed."; exit 5
    }
}

Write-Host "Launching $Pkg..."
& $adb -s $serial shell am start -n "$Pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 8

# ---- capture
$logcatPath     = Join-Path $logs "rescue911_stable_logcat.txt"
$screenshotPath = Join-Path $logs "rescue911_stable_screenshot.png"
& $adb -s $serial logcat -d 2>$null | Out-File -Encoding ascii $logcatPath
& $adb -s $serial exec-out screencap -p > $screenshotPath

# ---- foreground confirmation
$top = & $adb -s $serial shell "dumpsys activity activities | grep topResumedActivity"
$pid_app = (& $adb -s $serial shell pidof $Pkg).Trim()
Write-Host "topResumedActivity: $top"
Write-Host "PID of $Pkg : $pid_app"

# ---- crash scan -- ONLY for our package
$logcatText = Get-Content $logcatPath -Raw
$appCrashes = @()

# 1) FATAL EXCEPTION followed within ~10 lines by our package
foreach ($m in [regex]::Matches($logcatText, "FATAL EXCEPTION")) {
    $tail = $logcatText.Substring($m.Index, [Math]::Min(2000, $logcatText.Length - $m.Index))
    if ($tail -match [regex]::Escape($Pkg)) { $appCrashes += "FATAL EXCEPTION near $Pkg" }
}
# 2) Explicit "ANR in <pkg>"
if ($logcatText -match ("ANR in " + [regex]::Escape($Pkg))) {
    $appCrashes += "ANR in $Pkg"
}
# 3) Process: <pkg>  (Android crash banner format)
if ($logcatText -match ("Process: " + [regex]::Escape($Pkg))) {
    $appCrashes += "Process: $Pkg crash banner"
}
# 4) Unable to start activity for our package
if ($logcatText -match ("Unable to start activity .*" + [regex]::Escape($Pkg))) {
    $appCrashes += "Unable to start activity $Pkg"
}

# ---- system ANRs are warnings only
$systemAnrCount = ([regex]::Matches($logcatText, "ANR in (?!" + [regex]::Escape($Pkg) + ")[A-Za-z0-9_.]+")).Count

Write-Host ""
Write-Host "Logcat:     $logcatPath"
Write-Host "Screenshot: $screenshotPath"
Write-Host "System/Google ANRs in logcat (warnings, not failures): $systemAnrCount"

if ($appCrashes.Count -gt 0) {
    Write-Host "[FAIL] App-level failure detected:"
    $appCrashes | ForEach-Object { Write-Host "  - $_" }
    exit 6
}

Write-Host "[ OK ] No app-level FATAL EXCEPTION / ANR / start-failure for $Pkg."
Write-Host "[ OK ] App PID $pid_app is foreground."
exit 0
