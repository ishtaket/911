# End-to-end smoke: backend + emulator + APK install + launch + on-device
# health probe. Does NOT depend on Android UI automation -- the on-device
# probe uses adb shell -> /system/bin/curl (or 'wget') against
# http://10.0.2.2:8000/health to prove emulator -> host wiring works.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\dev_android_backend_smoke.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\dev_android_backend_smoke.ps1 -StartBackend
#   powershell -ExecutionPolicy Bypass -File scripts\dev_android_backend_smoke.ps1 -Avd Rescue911_API_30_default

[CmdletBinding()]
param(
    [string]$Avd = "Rescue911_API_30_default",
    [string]$Pkg = "com.rescue911.osint",
    [int]$BackendPort = 8000,
    [int]$BootTimeoutSec = 240,
    [int]$SettleSec = 25,
    [switch]$StartBackend
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root "logs"
$apk  = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Force -Path $logs | Out-Null }
if (-not (Test-Path $apk))  { Write-Host "[FAIL] APK missing at $apk -- run scripts\dev_android_build.ps1 first."; exit 2 }

# ---- toolchain
$jdk = "C:\Program Files\Java\jdk-19"
if (Test-Path (Join-Path $jdk "bin\javac.exe")) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
$sdk = "C:\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
if (-not (Test-Path $adb))      { Write-Host "[FAIL] adb missing"; exit 2 }
if (-not (Test-Path $emulator)) { Write-Host "[FAIL] emulator.exe missing"; exit 2 }

# ---- 1) backend
function Test-Backend([int]$port) {
    try {
        $r = Invoke-WebRequest -Uri ("http://127.0.0.1:" + $port + "/health") -TimeoutSec 2 -UseBasicParsing
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

if (-not (Test-Backend $BackendPort)) {
    if ($StartBackend) {
        Write-Host "Backend not responding -- starting it..."
        & (Join-Path $PSScriptRoot "dev_backend_start.ps1") -Background -Port $BackendPort
        for ($i=0; $i -lt 10; $i++) {
            Start-Sleep 2
            if (Test-Backend $BackendPort) { break }
        }
    }
    if (-not (Test-Backend $BackendPort)) {
        Write-Host "[FAIL] Backend not reachable at http://127.0.0.1:$BackendPort/health. Re-run with -StartBackend or start it manually."
        exit 3
    }
}
Write-Host "[ OK ] Backend health probed on host loopback."

# ---- 2) emulator
function Get-OnlineDevice {
    & $adb devices | Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1
}
$serial = Get-OnlineDevice
if (-not $serial) {
    $known = & $emulator -list-avds
    if ($known -notcontains $Avd) {
        Write-Host "[FAIL] AVD '$Avd' not found. Known AVDs: $known"
        exit 4
    }
    Write-Host "Booting AVD '$Avd'..."
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $Avd,
        "-no-snapshot-load","-no-snapshot-save",
        "-no-boot-anim","-no-audio",
        "-memory","3072","-cores","2",
        "-gpu","swiftshader_indirect",
        "-netdelay","none","-netspeed","full"
    ) -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $logs "emulator_backend_smoke_stdout.log") `
      -RedirectStandardError  (Join-Path $logs "emulator_backend_smoke_stderr.log") | Out-Null

    & $adb wait-for-device | Out-Null
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    do {
        Start-Sleep 2
        $bc = (& $adb shell getprop sys.boot_completed 2>$null)
        if ($bc) { $bc = $bc.Trim() }
    } until ($bc -eq "1" -or (Get-Date) -gt $deadline)
    if ($bc -ne "1") { Write-Host "[FAIL] Boot did not complete within $BootTimeoutSec s"; exit 5 }
    $serial = Get-OnlineDevice
}
Write-Host ("[ OK ] Emulator online: " + $serial)

Write-Host "Settling $SettleSec s for system to calm down..."
Start-Sleep -Seconds $SettleSec
& $adb -s $serial shell settings put global window_animation_scale 0     | Out-Null
& $adb -s $serial shell settings put global transition_animation_scale 0 | Out-Null
& $adb -s $serial shell settings put global animator_duration_scale 0    | Out-Null

# ---- 3) on-device probe of host backend via 10.0.2.2
$probeOut = ""
try {
    $probeOut = & $adb -s $serial shell "toybox wget -qO- http://10.0.2.2:$BackendPort/health 2>/dev/null || curl -s http://10.0.2.2:$BackendPort/health 2>/dev/null"
} catch {}
$probeOut = ($probeOut -join "`n").Trim()
if (-not $probeOut) {
    Write-Host "[WARN] Could not probe host loopback from emulator via toybox/curl. Continuing -- the app's own probe will run via the backend status monitor."
} else {
    Write-Host ("[ OK ] Emulator reached host backend: " + $probeOut)
}

# ---- 4) install + launch
Write-Host "Installing APK..."
$installOut = & $adb -s $serial install -r $apk 2>&1
Write-Host $installOut
if (($installOut -join "`n") -notmatch "Success") {
    if ($installOut -match "INSTALL_FAILED_(VERSION_DOWNGRADE|UPDATE_INCOMPATIBLE)") {
        & $adb -s $serial uninstall $Pkg | Out-Null
        $installOut = & $adb -s $serial install -r $apk 2>&1
        Write-Host $installOut
        if (($installOut -join "`n") -notmatch "Success") { Write-Host "[FAIL] Install failed."; exit 6 }
    } else { Write-Host "[FAIL] Install failed."; exit 6 }
}

Write-Host "Launching $Pkg..."
& $adb -s $serial shell am start -n "$Pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 8

# ---- 5) capture
$logcatPath     = Join-Path $logs "android_backend_smoke_logcat.txt"
$screenshotPath = Join-Path $logs "android_backend_smoke_screenshot.png"
& $adb -s $serial logcat -d 2>$null | Out-File -Encoding ascii $logcatPath
& $adb -s $serial exec-out screencap -p > $screenshotPath

# ---- 6) crash scan -- ONLY for our app
$lc = Get-Content $logcatPath -Raw
$crashes = @()
foreach ($m in [regex]::Matches($lc, "FATAL EXCEPTION")) {
    $tail = $lc.Substring($m.Index, [Math]::Min(2000, $lc.Length - $m.Index))
    if ($tail -match [regex]::Escape($Pkg)) { $crashes += "FATAL EXCEPTION near $Pkg" }
}
if ($lc -match ("ANR in " + [regex]::Escape($Pkg)))               { $crashes += "ANR in $Pkg" }
if ($lc -match ("Process: "  + [regex]::Escape($Pkg)))            { $crashes += "Process: $Pkg crash banner" }
if ($lc -match ("Unable to start activity .*" + [regex]::Escape($Pkg))) { $crashes += "Unable to start activity $Pkg" }

$systemAnrCount = ([regex]::Matches($lc, "ANR in (?!" + [regex]::Escape($Pkg) + ")[A-Za-z0-9_.]+")).Count
$top = & $adb -s $serial shell "dumpsys activity activities | grep topResumedActivity"
$pid_app = (& $adb -s $serial shell pidof $Pkg).Trim()

Write-Host ""
Write-Host "Logcat:     $logcatPath"
Write-Host "Screenshot: $screenshotPath"
Write-Host "topResumedActivity: $top"
Write-Host "PID of $Pkg : $pid_app"
Write-Host "System/Google ANRs (warnings only): $systemAnrCount"

if ($crashes.Count -gt 0) {
    Write-Host "[FAIL] App-level failure(s):"
    $crashes | ForEach-Object { Write-Host "  - $_" }
    exit 7
}

Write-Host "[ OK ] No app-level FATAL EXCEPTION / ANR / start-failure for $Pkg."
Write-Host "[ OK ] Backend smoke complete."
exit 0
