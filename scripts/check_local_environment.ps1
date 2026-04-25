# Reports on local prerequisites. Read-only; never installs anything.
$ErrorActionPreference = "Continue"

function Write-Status($name, $level, $detail) {
    $tag = switch ($level) {
        "PASS" { "[ OK ]" }
        "WARN" { "[WARN]" }
        "FAIL" { "[FAIL]" }
    }
    Write-Host "$tag $name $detail"
}

function Probe($cmd) {
    try { & $cmd 2>&1 | Out-String } catch { "" }
}

Write-Host "Rescue911 - local environment check"
Write-Host "===================================="

# Windows + PowerShell
Write-Status "Windows" "PASS" $([System.Environment]::OSVersion.VersionString)
Write-Status "PowerShell" "PASS" $PSVersionTable.PSVersion.ToString()

# git
$gitOut = Probe { git --version }
if ($gitOut -match "git version") { Write-Status "git" "PASS" $gitOut.Trim() } else { Write-Status "git" "FAIL" "not on PATH" }

# Docker
$dockerOut = Probe { docker --version }
if ($dockerOut -match "Docker version") {
    Write-Status "Docker (CLI)" "PASS" $dockerOut.Trim()
    $composeOut = Probe { docker compose version }
    if ($composeOut -match "Docker Compose") { Write-Status "Docker Compose" "PASS" $composeOut.Trim() } else { Write-Status "Docker Compose" "WARN" "compose v2 not detected" }
    $infoOut = Probe { docker info --format "{{.ServerVersion}}" }
    if ($infoOut.Trim()) { Write-Status "Docker daemon" "PASS" "ServerVersion=$($infoOut.Trim())" } else { Write-Status "Docker daemon" "WARN" "not running - start Docker Desktop" }
} else { Write-Status "Docker" "FAIL" "not on PATH (Docker Desktop required)" }

# Java
$javaOut = Probe { java -version }
if ($javaOut -match "version") { Write-Status "Java" "PASS" ($javaOut -split "`n")[0].Trim() } else { Write-Status "Java" "FAIL" "JDK 17 recommended for AGP 8.x" }

# Python
$pythonOut = Probe { python --version }
if ($pythonOut -match "Python") { Write-Status "Python" "PASS" $pythonOut.Trim() } else {
    $pyOut = Probe { py --version }
    if ($pyOut -match "Python") { Write-Status "Python (py)" "PASS" $pyOut.Trim() } else { Write-Status "Python" "FAIL" "Python 3.12 required" }
}

# Node
$nodeOut = Probe { node --version }
if ($nodeOut -match "^v") { Write-Status "Node.js" "PASS" $nodeOut.Trim() } else { Write-Status "Node.js" "WARN" "not on PATH (optional)" }

# ADB
$adbPath = $null
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) { $adbPath = $adbCmd.Path }
elseif (Test-Path "C:\platform-tools\adb.exe") { $adbPath = "C:\platform-tools\adb.exe" }

if ($adbPath) {
    $adbVer = & $adbPath version 2>&1 | Select-Object -First 1
    Write-Status "ADB" "PASS" "$adbPath ($adbVer)"
} else {
    Write-Status "ADB" "FAIL" "not on PATH and not at C:\platform-tools\adb.exe"
}

# Android SDK env (probe also fixed location used by our installer)
$home1 = $env:ANDROID_HOME
$home2 = $env:ANDROID_SDK_ROOT
$installed = "C:\Android\Sdk"
if ($home1) { Write-Status "ANDROID_HOME" "PASS" $home1 } else { Write-Status "ANDROID_HOME" "WARN" "not set" }
if ($home2) { Write-Status "ANDROID_SDK_ROOT" "PASS" $home2 } else { Write-Status "ANDROID_SDK_ROOT" "WARN" "not set" }
$sdkDir = if ($home1 -and (Test-Path $home1)) { $home1 } elseif ($home2 -and (Test-Path $home2)) { $home2 } elseif (Test-Path $installed) { $installed } else { $null }
if ($sdkDir) {
    $haveCmd = Test-Path "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
    $havePlatform = Test-Path "$sdkDir\platforms\android-34"
    $haveBuild = Test-Path "$sdkDir\build-tools\34.0.0"
    $havePT = Test-Path "$sdkDir\platform-tools\adb.exe"
    $detail = "at $sdkDir; cmdline=$haveCmd platform-tools=$havePT android-34=$havePlatform build-tools-34=$haveBuild"
    if ($haveCmd -and $havePlatform -and $haveBuild -and $havePT) {
        Write-Status "Android SDK" "PASS" $detail
    } else {
        Write-Status "Android SDK" "WARN" "$detail (missing components)"
    }
} else {
    Write-Status "Android SDK" "FAIL" "Set ANDROID_HOME or ANDROID_SDK_ROOT (gradle android build will fail)"
}

# Java home for SDK / Gradle (Oracle JRE 8 won't work)
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    Write-Status "JAVA_HOME (JDK)" "PASS" $env:JAVA_HOME
} else {
    foreach ($jdk in @("C:\Program Files\Java\jdk-19","C:\Program Files\Java\jdk-17","C:\Program Files\Eclipse Adoptium\jdk-17")) {
        if (Test-Path "$jdk\bin\javac.exe") { Write-Status "JAVA_HOME (JDK)" "WARN" "JAVA_HOME unset; recommend $jdk"; break }
    }
}

# Emulator
$emuCmd = $null
foreach ($p in @($home1, $home2)) {
    if ($p -and (Test-Path "$p\emulator\emulator.exe")) { $emuCmd = "$p\emulator\emulator.exe"; break }
}
if ($emuCmd) {
    $list = & $emuCmd -list-avds 2>&1 | Out-String
    Write-Status "Android emulator" "PASS" "AVDs: $($list.Trim())"
} else {
    Write-Status "Android emulator" "WARN" "not found"
}

Write-Host ""
Write-Host "Done."
