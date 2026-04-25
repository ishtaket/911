# Local environment (Windows)

Findings from `scripts\check_local_environment.ps1` on this machine after the Android SDK install (2026-04-25).

| Tool | Status | Detail |
| --- | --- | --- |
| Windows | OK | Microsoft Windows NT 10.0.26200.0 |
| PowerShell | OK | 5.1.26100.8115 |
| git | OK | git version 2.42.0.windows.2 |
| Docker CLI | OK | Docker version 29.4.0 |
| Docker Compose | OK | v5.1.2 |
| Docker daemon | OK | running |
| Java | OK | JDK 19.0.2 at `C:\Program Files\Java\jdk-19` (also JRE 8 at `C:\Program Files\Java\jre1.8.0_351` — must NOT be first on PATH for Android) |
| Python | OK | 3.12.0 |
| Node.js | OK | v24.14.0 (optional) |
| ADB | OK | `C:\Android\Sdk\platform-tools\adb.exe` plus legacy `C:\platform-tools\adb.exe` |
| `ANDROID_HOME` | **OK** | `C:\Android\Sdk` (persisted at User scope) |
| `ANDROID_SDK_ROOT` | **OK** | `C:\Android\Sdk` (persisted at User scope) |
| Android cmdline-tools | OK | `C:\Android\Sdk\cmdline-tools\latest` (sdkmanager 12.0) |
| Android platforms | OK | `android-34` |
| Android build-tools | OK | `34.0.0` |
| Android platform-tools | OK | installed |
| Android emulator | **WARN** | not installed (skipped to save disk; ~500 MB + system images add ~1.5 GB) |
| Free disk on `C:` | **TIGHT** | ~2.8 GB after install (was 1.2 GB before) |

## What's installed and where

```
C:\Android\Sdk\
├── cmdline-tools\latest\         (sdkmanager / avdmanager / lint / ...)
├── platform-tools\               (adb.exe, fastboot, etc.)
├── platforms\android-34\         (compileSdk target)
├── build-tools\34.0.0\           (aapt2, d8, apksigner, zipalign, ...)
└── licenses\                     (auto-accepted)
```

Persistent env vars (set at User scope):
- `ANDROID_HOME = C:\Android\Sdk`
- `ANDROID_SDK_ROOT = C:\Android\Sdk`

Per-session helpful exports (the build script handles this automatically):
- `JAVA_HOME = C:\Program Files\Java\jdk-19`
- `PATH` should put `%JAVA_HOME%\bin` **before** any Oracle JRE 8 entry.

## Java caveat

Two Java installs are on the machine:
- `C:\Program Files\Java\jdk-19` — full JDK, has `javac` (use this)
- `C:\Program Files\Java\jre1.8.0_351` — JRE 8 only, **incompatible** with the Android command-line tools (which require Java 17 class files)

The Oracle javapath `C:\Program Files\Common Files\Oracle\Java\javapath` resolves to JRE 8 first by default. The build script (`scripts/dev_android_build.ps1`) sets `JAVA_HOME` and prepends `%JAVA_HOME%\bin` to the session PATH to avoid this trap.

## Emulator (deferred)

To run `:app:connectedDebugAndroidTest` and `scripts/dev_android_smoke.ps1` against an emulator, install:

```powershell
# Free at least 2 GB on C:\ first.
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --install `
    "emulator" `
    "system-images;android-34;google_apis;x86_64"

# Re-create the AVD (existing config at ~/.android/avd/Pixel_6_API_34 is preserved):
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n Pixel_6_API_34 -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force

# Launch:
Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList "-avd","Pixel_6_API_34","-no-snapshot"
```

## Build verification (this session)

- `:app:assembleDebug` ✅ produced `android\app\build\outputs\apk\debug\app-debug.apk` (17.4 MB).
- `:app:testDebugUnitTest` ✅ 7/7 unit tests passing (4 in `MockDataTest`, 3 in `ValidationTest`).
- `connectedDebugAndroidTest` not run — no device/emulator connected.
