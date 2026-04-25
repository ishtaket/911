# Build the Android debug APK.
# - Auto-detects a real JDK (17/19) so we don't accidentally use Oracle JRE 8.
# - Bootstraps the Gradle wrapper if the wrapper jar is missing.
# - Writes android\local.properties with the resolved sdk.dir.
# - Requires ANDROID_HOME (or ANDROID_SDK_ROOT) to assemble.
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\android")
Set-Location $root

# --- Resolve JAVA_HOME (must point to a JDK with javac, not a JRE) ---
function Test-JdkPath($p) {
    return $p -and (Test-Path $p) -and (Test-Path (Join-Path $p "bin\javac.exe"))
}

if (-not (Test-JdkPath $env:JAVA_HOME)) {
    foreach ($candidate in @(
        "C:\Program Files\Eclipse Adoptium\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-17",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-19",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Microsoft\jdk-17",
        "C:\Program Files\Microsoft\jdk-21"
    )) {
        if (Test-JdkPath $candidate) {
            Write-Host "Auto-detected JDK: $candidate"
            $env:JAVA_HOME = $candidate
            break
        }
    }
}

if (-not (Test-JdkPath $env:JAVA_HOME)) {
    Write-Host "[FAIL] No JDK with javac.exe found. Install Eclipse Temurin 17 or 21."
    Write-Host "       (JRE 8 alone is NOT enough -- Android cmdline-tools require Java 17+.)"
    exit 2
}

# Put JAVA_HOME first on PATH so 'java' resolves to JDK, not Oracle JRE 8 javapath.
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# --- Resolve Android SDK ---
$sdkDir = $null
foreach ($p in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "C:\Android\Sdk")) {
    if ($p -and (Test-Path $p)) { $sdkDir = $p; break }
}
if (-not $sdkDir) {
    Write-Host "[FAIL] No Android SDK found."
    Write-Host "       Set ANDROID_HOME, e.g. setx ANDROID_HOME `"C:\Android\Sdk`""
    Write-Host "       Or run scripts\install_missing_prereqs.ps1 (TODO: SDK install path)."
    exit 2
}
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

# Write local.properties so AGP doesn't have to guess.
Set-Content -Path "local.properties" -Value ("sdk.dir=" + $sdkDir.Replace("\","/")) -Encoding ascii
Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"

# --- Bootstrap Gradle wrapper if missing ---
$wrapperJar = "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "Gradle wrapper jar missing - bootstrapping..."
    $gradleVersion = "8.6"

    # Try a wrapper distribution that the user already has cached.
    $wrapperDists = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-$gradleVersion-bin"
    $cachedGradle = $null
    if (Test-Path $wrapperDists) {
        $cachedGradle = Get-ChildItem $wrapperDists -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match "gradle-$gradleVersion\\bin\\gradle.bat$" } | Select-Object -First 1
    }

    if ($cachedGradle) {
        Write-Host "Using cached Gradle: $($cachedGradle.FullName)"
        & $cachedGradle.FullName wrapper --gradle-version=$gradleVersion --distribution-type=bin
    } else {
        $cacheRoot = Join-Path $env:LOCALAPPDATA "Rescue911\gradle"
        $gradleHome = Join-Path $cacheRoot "gradle-$gradleVersion"
        if (-not (Test-Path "$gradleHome\bin\gradle.bat")) {
            New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
            $zip = Join-Path $cacheRoot "gradle-$gradleVersion-bin.zip"
            if (-not (Test-Path $zip)) {
                Write-Host "Downloading Gradle $gradleVersion..."
                Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
            }
            Write-Host "Extracting..."
            Expand-Archive -Path $zip -DestinationPath $cacheRoot -Force
        }
        Write-Host "Materializing Gradle wrapper..."
        & "$gradleHome\bin\gradle.bat" wrapper --gradle-version=$gradleVersion --distribution-type=bin
    }
}

Write-Host "Running .\gradlew.bat :app:assembleDebug ..."
.\gradlew.bat :app:assembleDebug --console=plain
