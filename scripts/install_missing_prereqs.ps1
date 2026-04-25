# Safe, non-destructive prerequisite installer for Windows.
# Checks before every install. Prefers winget. Never reinstalls existing tools.
$ErrorActionPreference = "Stop"

$winget = Get-Command winget -ErrorAction SilentlyContinue

function Has($cmd) { (Get-Command $cmd -ErrorAction SilentlyContinue) -ne $null }

function Suggest-Or-Install($name, $wingetId, $detectCmd) {
    if (Has $detectCmd) {
        Write-Host "[ OK ] $name already installed (skipping)"
        return
    }
    if ($winget) {
        Write-Host "[INFO] Installing $name via winget ($wingetId)..."
        winget install --id $wingetId --silent --accept-source-agreements --accept-package-agreements --disable-interactivity
    } else {
        Write-Host "[WARN] winget not available. Install $name manually."
    }
}

Write-Host "Rescue911 - install missing prereqs (safe, won't reinstall)"

# Docker Desktop
if (Has "docker") {
    Write-Host "[ OK ] Docker already installed (skipping)"
} else {
    Suggest-Or-Install "Docker Desktop" "Docker.DockerDesktop" "docker"
}

# Java (Temurin 17)
if (Has "java") {
    Write-Host "[ OK ] Java already installed (skipping)"
} else {
    Suggest-Or-Install "Eclipse Temurin JDK 17" "EclipseAdoptium.Temurin.17.JDK" "java"
}

# Python 3.12
if (Has "python" -or (Has "py")) {
    Write-Host "[ OK ] Python already installed (skipping)"
} else {
    Suggest-Or-Install "Python 3.12" "Python.Python.3.12" "python"
}

# Git
if (Has "git") {
    Write-Host "[ OK ] Git already installed (skipping)"
} else {
    Suggest-Or-Install "Git" "Git.Git" "git"
}

# ADB / platform-tools
$adbAt = "C:\platform-tools\adb.exe"
if (Has "adb" -or (Test-Path $adbAt)) {
    Write-Host "[ OK ] ADB present (skipping)"
} else {
    Write-Host "[INFO] Install Android Platform Tools manually:"
    Write-Host "       https://developer.android.com/tools/releases/platform-tools"
    Write-Host "       Recommended location: C:\platform-tools\"
}

# Android SDK
if ($env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT) {
    Write-Host "[ OK ] Android SDK env var set (skipping)"
} else {
    Write-Host "[WARN] ANDROID_HOME / ANDROID_SDK_ROOT not set."
    Write-Host "       Install command-line tools or Android Studio, then set:"
    Write-Host "       setx ANDROID_HOME `"C:\Android\Sdk`""
}

Write-Host "Done."
