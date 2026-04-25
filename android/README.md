# Rescue911 Android

Native Kotlin/Compose operator app — package `com.rescue911.osint`.

## Build prerequisites

- Android SDK with `compileSdk=34` and `minSdk=26` platforms.
- Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK path.
- Java 17 on `PATH` (current dev box has Java 19, AGP 8.5 supports both).
- ADB on `PATH` (or fall through to `C:\platform-tools\adb.exe`).

## First-time bootstrap

This project ships **without** the `gradle-wrapper.jar` binary. The first build step
must obtain one. From PowerShell:

```powershell
.\scripts\dev_android_build.ps1
```

The script will:
1. Detect Java + Android SDK (or warn).
2. Download Gradle 8.10.2 if missing.
3. Run `gradle wrapper --gradle-version=8.10.2 --distribution-type=bin` inside `android/` to materialize `gradlew.bat` + `gradle-wrapper.jar`.
4. Run `.\gradlew.bat :app:assembleDebug`.

After bootstrap, `android/gradlew.bat` works directly.

## Tests

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest   # requires emulator / device
```

## Smoke (ADB)

```powershell
.\scripts\dev_android_smoke.ps1
```

Builds debug APK → installs → launches → captures logcat → reports.
