# Android smoke-test report

Date: 2026-04-25
Working directory: `C:\Users\Dmitriy\Projects\911`
Branch: `feat/heat-map-zones`

## Outcome at a glance

| Step | Result |
|---|---|
| App installed successfully | **YES** — `Performing Streamed Install / Success` |
| App launched successfully | **YES** — `topResumedActivity=ActivityRecord{… com.rescue911.osint/.MainActivity}` |
| No `FATAL EXCEPTION` found in logcat | **YES** — `grep -c "FATAL EXCEPTION" = 0` |
| Screenshot captured | **YES** — `logs/android_smoke_screenshot.png` (118 KB, shows the rendered Search Dashboard) |

End-to-end smoke test of the existing `app-debug.apk` against a freshly-installed emulator + system image, booted from the existing user AVD shell. **No app code changed.**

## 1. Java / `JAVA_HOME` used

`JAVA_HOME = C:\Program Files\Java\jdk-19` (set for this session only). `java -version` reported `19.0.2 2023-01-17`. `javac.exe` confirmed at `$JAVA_HOME\bin\javac.exe`. The default `java` on the user PATH is still JRE 8 (Oracle javapath). Persisted via `setx JAVA_HOME …` was NOT done in this milestone — only the in-session export.

## 2. Free disk before install

`Get-PSDrive C` → **Used 171.93 GB, Free 49.10 GB** before install. Well above the 4 GB threshold required by the runbook.

## 3. SDK packages installed in this milestone

Via `C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat`:

1. `--licenses` (non-interactive, all 7+ pending licenses accepted) → `All SDK package licenses accepted.`
2. `--install "emulator" "system-images;android-34;google_apis;x86_64"` → exit code 0. The system image unzipped to 100% (~1.5 GB).

Nothing else was installed; no Android Studio, no Docker, no extra packages.

## 4. emulator.exe status

**Present** at `C:\Android\Sdk\emulator\emulator.exe`. Reported version: `Android emulator version 36.5.11.0 (build_id 15261927)`. Found system path: `C:\Android\Sdk\system-images\android-34\google_apis\x86_64\`.

## 5. System image status

**Present** at `C:\Android\Sdk\system-images\android-34\google_apis\x86_64\` (contains `build.prop`, `data/`, `VerifiedBootParams.textproto`, `advancedFeatures.ini`, `NOTICE.txt`, …). This is the image the existing `Pixel_6_API_34` AVD targets.

## 6. AVD used

**`Pixel_6_API_34`** — the existing user AVD at `C:\Users\Dmitriy\.android\avd\Pixel_6_API_34.avd`. **Not deleted, not recreated.** No new AVD was created.

`avdmanager list avd` confirms:
- Name: `Pixel_6_API_34`
- Device: `pixel_6 (Google)`
- Target: `Google APIs (Google Inc.)`, Android 14 ("UpsideDownCake"), `google_apis/x86_64`
- Sdcard: 512 MB

`emulator -list-avds` confirms the same.

## 7. Boot result

Started cold (`-no-snapshot-load -no-audio -netdelay none -netspeed full`) as a hidden background process. Polled `adb shell getprop sys.boot_completed`. **Result: BOOTED after 66 s.**

Emulator startup logs at `logs/emulator_stdout.log` (12 KB) and `logs/emulator_stderr.log` (1 KB).

## 8. `adb devices` result

```
List of devices attached
emulator-5554   device
```

## 9. APK install result

```
$ adb install -r C:/Users/Dmitriy/Projects/911/android/app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
```

No conflict, no `INSTALL_FAILED_*`. APK source: existing 17.4 MB debug build from the prior milestone (no rebuild needed; the smoke script's incremental `:app:assembleDebug` returned UP-TO-DATE in 41 s).

## 10. App launch result

Launched twice — first via `adb shell monkey -p com.rescue911.osint -c android.intent.category.LAUNCHER 1` (manual phase 5), then again via `adb shell am start -n com.rescue911.osint/com.rescue911.osint.MainActivity` (the `dev_android_smoke.ps1` script, phase 6). Both succeeded.

ActivityManager confirmation:

```
ActivityTaskManager: START u0 {flg=0x10000000 cmp=com.rescue911.osint/.MainActivity}
                     with LAUNCH_MULTIPLE from uid 2000 (BAL_ALLOW_PERMISSION) result code=0
ActivityManager:     Start proc 4390:com.rescue911.osint/u0a193 for next-top-activity
                     {com.rescue911.osint/com.rescue911.osint.MainActivity}
…
dumpsys activity activities → topResumedActivity=ActivityRecord{… com.rescue911.osint/.MainActivity t56}
```

ART runtime startup observed: `Late-enabling -Xcheck:jni`, `Using CollectorTypeCC GC`, `target_sdk_version=34`, native loader configured against `base.apk!/lib/x86_64`. The MainActivity registered an `OnBackInvokedCallback`.

## 11. Crash check result

`grep -c "FATAL EXCEPTION" logs/android_smoke_logcat.txt` → **0**. The script's own check confirmed: `[ OK ] No FATAL EXCEPTION detected in last 300 lines.`

Other patterns scanned (FATAL EXCEPTION / AndroidRuntime / ANR / Process: com.rescue911.osint / java.lang.RuntimeException / Unable to start activity):

| Hit | Origin | Verdict |
|---|---|---|
| `ANR in com.android.phone` | system app | not our app — fresh-boot system noise |
| `ANR in com.google.android.as` | Android System Intelligence | not our app |
| `ANR in com.google.android.googlequicksearchbox:search` | Google Search | not our app |
| `ANR in com.google.android.gm` | Gmail | not our app |
| `RuntimeException: RIL_REQUEST_GET_HARDWARE_CONFIG …` | radio interface layer | emulator quirk (no real radio), severity `W` |
| `MendelPackageState … BuglePhoneApplicationBase` | Google Messages | not our app |
| `AndroidRuntime: Calling main entry com.android.commands.monkey.Monkey` | monkey runner itself | informational, exits cleanly with `result code 0` |
| `Unexpected CPU variant for x86: x86_64. Known variants: atom, sandybridge, …` | ART, our process | harmless ART notice on x86_64 emulator |
| `Unable to open '…/base.dm': No such file or directory` | our process | optional DEX metadata file, harmless |
| `JNI critical lock held for 17.143ms on Thread WM.task-1` | our process, startup | startup-only, not a fault |

**No crash, no ANR, no fatal exception attributable to `com.rescue911.osint`.**

## 12. Logcat path

`C:\Users\Dmitriy\Projects\911\logs\android_smoke_logcat.txt` — 1 759 lines / 222 KB, full `adb logcat -d` taken after the second launch.

Companion files in the same directory:
- `logs/smoke_script_output.txt` (16 KB) — full output of `scripts/dev_android_smoke.ps1`.
- `logs/emulator_stdout.log` (12 KB), `logs/emulator_stderr.log` (1 KB) — emulator boot output.

## 13. Screenshot path

`C:\Users\Dmitriy\Projects\911\logs\android_smoke_screenshot.png` (120 671 B). Captured via `adb exec-out screencap -p`.

The screenshot shows the rendered **Search Dashboard** screen with the dark-navy Rescue911 theme:
- Header: "Search dashboard"
- Cards: "Web search — Brave / Google CSE / SerpAPI fan-out, EN/HE/RU/AR variants."; "Public social search — FB / IG / TT / YT / TG / Reddit / X / VK / LinkedIn — public only."; "Archive search — Wayback / Common Crawl / public mirrors."; "GeoINT pipeline — EXIF → OCR → Vision → GeoSeer / Picarta → POI validate."
- Bottom navigation bar with five tabs: Cases / Review / **Map** (selected) / Audit / Settings
- English locale (default emulator language)

(An earlier screenshot in the same run caught the system "System UI isn't responding" ANR overlay during the fresh-boot CPU thrash. The app itself was still `topResumedActivity` underneath. After 45 s of system stabilization plus a `KEYCODE_BACK` to dismiss the dialog and a re-`am start`, the app surface was clear and the final screenshot above was captured. The ANR was in `com.android.systemui` / Google system processes, not in `com.rescue911.osint`.)

## 14. Blockers

**None blocking the milestone.** The following are minor follow-ups, not blockers:

1. `JAVA_HOME` is still not persisted at User scope. Future bare-shell `sdkmanager` / `avdmanager` / `gradle` calls outside the build script will hit the JRE 8 trap. One-shot fix: `setx JAVA_HOME "C:\Program Files\Java\jdk-19"`.
2. `logs/` is not in `.gitignore`. `android_smoke_logcat.txt` may contain device-identifying noise (IPv6 addresses, package names). Recommend adding `logs/` to `.gitignore` before the next commit so these never get staged.
3. `scripts/dev_android_smoke.ps1` line 1 contains a non-ASCII arrow `→` in a comment. PS 5.1 ran the file successfully (the byte sequence happens to land on harmless tokens), but other non-ASCII in PS scripts is a known parser hazard — see the em-dash incident in `dev_android_build.ps1` from the previous milestone. Cosmetic.
4. The smoke script reports `result code=0` for `am start` but a real "the app's first screen rendered correctly" check is not automated. The screenshot lets a human verify the rescue-themed Cases dashboard came up.
5. No backend was running during the smoke test — the app's repo binding is `MockRescue911Repository` (per `core/di/AppModule.kt`), so no network call was attempted. To smoke against the live backend, set `apiBaseUrl = http://10.0.2.2:8000/` in app prefs (default already does this) and start the backend separately.

## 15. Exact next commands

To re-run the smoke later in a fresh shell (the emulator may need to be restarted):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
$env:Path = "$env:JAVA_HOME\bin;C:\Android\Sdk\platform-tools;$env:Path"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Android\Sdk"

# Boot the existing AVD if not already running
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" `
    -ArgumentList "-avd","Pixel_6_API_34","-no-snapshot-load","-no-audio" `
    -WindowStyle Hidden `
    -RedirectStandardOutput "C:\Users\Dmitriy\Projects\911\logs\emulator_stdout.log" `
    -RedirectStandardError  "C:\Users\Dmitriy\Projects\911\logs\emulator_stderr.log"

# Wait for boot
& "$env:ANDROID_HOME\platform-tools\adb.exe" wait-for-device
do { $bc = (& "$env:ANDROID_HOME\platform-tools\adb.exe" shell getprop sys.boot_completed 2>$null).Trim(); Start-Sleep 2 } until ($bc -eq "1")

# Run the canonical smoke (build → install → launch → log scan)
powershell -ExecutionPolicy Bypass -File C:\Users\Dmitriy\Projects\911\scripts\dev_android_smoke.ps1
```

To stop the emulator without rebooting Windows:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 emu kill
```

To re-capture artifacts only (without re-launching):

```powershell
$ADB = "C:\Android\Sdk\platform-tools\adb.exe"
& $ADB logcat -d > C:\Users\Dmitriy\Projects\911\logs\android_smoke_logcat.txt
& $ADB exec-out screencap -p > C:\Users\Dmitriy\Projects\911\logs\android_smoke_screenshot.png
```

To smoke against the real backend (mock-mode off, requires backend running on the host):

```powershell
# Host: start backend
Set-Location C:\Users\Dmitriy\Projects\911\backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# Emulator already maps host loopback to 10.0.2.2; AppPreferences default is http://10.0.2.2:8000/
```

Optional: persist the JDK so future shells do not need the export:

```powershell
setx JAVA_HOME "C:\Program Files\Java\jdk-19"
```

---

**Summary: emulator unblocked, AVD booted, APK installed, app launched, no FATAL EXCEPTION, screenshot captured. No commit, no `git reset`, no destructive action. The existing AVD is intact.**
