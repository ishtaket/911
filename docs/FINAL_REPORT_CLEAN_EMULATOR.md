# Clean emulator setup — Rescue911_API_30_default

Date: 2026-04-25
Working directory: `C:\Users\Dmitriy\Projects\911`
Branch: `feat/heat-map-zones`

## TL;DR

A clean, lightweight, **System-UI-ANR-free** emulator now exists for Rescue911 smoke testing. The two API-34 AVDs were deleted; a fresh `Rescue911_API_30_default` AVD was created on the Android 11 default x86_64 image. The Rescue911 APK installs and launches, the **Cases** screen renders cleanly with no system overlay, and there are **0 ANRs of any kind** in the logcat — for the first time across all four runs in this investigation.

| Question | Answer |
|---|---|
| App installed | **YES** — `Performing Streamed Install / Success` |
| App launched | **YES** — PID 2420 |
| System UI / `system_server` ANR present | **NO** — `0` matches for `ANR in` in the entire 5000-line tail |
| `com.rescue911.osint` crashed | **NO** — 0 FATAL EXCEPTION, 0 `Process: com.rescue911.osint`, 0 `Unable to start activity`, 0 `ANR in com.rescue911.osint` |
| Recommended emulator | **`Rescue911_API_30_default`** |
| Recommended one-shot command | `powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1` |

## 1. AVDs found before delete

`logs/avd_inventory_before_delete.txt`. Two AVDs, both API-34, both flagged unstable in the previous milestone:

```
Pixel_6_API_34            (google_apis x86_64, pixel_6 device, sdcard 512 MB)
Pixel_6_API_34_default    (default     x86_64, pixel_6 device, sdcard 512 MB)
```

## 2. AVDs deleted

Both, via `avdmanager delete avd`:

```
$ avdmanager.bat delete avd -n Pixel_6_API_34
AVD 'Pixel_6_API_34' deleted.
$ avdmanager.bat delete avd -n Pixel_6_API_34_default
AVD 'Pixel_6_API_34_default' deleted.
```

Verified: `C:\Users\Dmitriy\.android\avd\` is empty of any `Pixel_6_API_34*` folder/`.ini`. `emulator -list-avds` returned no entries between Phase 3 and Phase 5.

`logs/avd_inventory_after_delete.txt` records the empty post-delete state.

## 3. Confirmation: SDK / project / adb keys / unrelated AVDs not deleted

| Path | Status |
|---|---|
| `C:\Android\Sdk\` | **untouched** (build-tools, cmdline-tools, emulator, licenses, platform-tools, platforms, system-images all intact) |
| `C:\Users\Dmitriy\Projects\911\` | **untouched** (no project file modified except scripts + docs noted in Phase 8) |
| `C:\Users\Dmitriy\.android\adbkey` | **present** (1 732 bytes, mtime 2026-03-21) |
| `C:\Users\Dmitriy\.android\adbkey.pub` | **present** (724 bytes, mtime 2026-03-21) |
| `C:\Users\Dmitriy\.android\analytics.settings` | **present** |
| `C:\Users\Dmitriy\.android\debug.keystore` | **present** (2 618 bytes) |
| Unrelated AVDs | none existed; nothing else was touched |

## 4. Android 30 default image install

The image was missing (`C:\Android\Sdk\system-images\android-30\` did not exist). Installed only the requested package via the existing `sdkmanager`:

```
$ sdkmanager.bat --install "system-images;android-30;default;x86_64"
exit 0
```

Log: `logs/sdk_install_api30_log.txt`. No Google APIs were installed. Verified afterwards: `C:\Android\Sdk\system-images\android-30\default\x86_64\` exists and contains the standard payload.

## 5. New AVD creation

```
$ avdmanager.bat create avd -n Rescue911_API_30_default \
    -k "system-images;android-30;default;x86_64" -d pixel_4 --force
Auto-selecting single ABI x86_64

$ avdmanager.bat list avd
    Name: Rescue911_API_30_default
  Device: pixel_4 (Google)
    Path: C:\Users\Dmitriy\.android\avd\Rescue911_API_30_default.avd
  Target: Default Android System Image
          Based on: Android 11.0 ("R") Tag/ABI: default/x86_64
  Sdcard: 512 MB
```

`emulator -list-avds` → `Rescue911_API_30_default`. `pixel_4` profile was used per the runbook (`pixel_5`/`pixel_3` were not needed).

## 6. Boot result

```powershell
emulator.exe -avd Rescue911_API_30_default `
  -no-snapshot-load -no-snapshot-save `
  -no-boot-anim -no-audio `
  -memory 3072 -cores 2 `
  -gpu swiftshader_indirect `
  -netdelay none -netspeed full
```

Boot completed in **68 s** (`adb shell getprop sys.boot_completed = 1`). Animations set to 0 / 0 / 0 immediately after boot.

Emulator stdout: `logs/rescue911_api30_emulator_stdout.log`.
Emulator stderr: `logs/rescue911_api30_emulator_stderr.log` (empty).

## 7. APK install result

```
$ adb install -r android\app\build\outputs\apk\debug\app-debug.apk
Performing Streamed Install
Success
```

minSdk in `android/app/build.gradle.kts` is `26`, so the API-30 device is fully compatible. No `INSTALL_FAILED_*` of any kind.

## 8. App launch result

```
$ adb shell am start -n com.rescue911.osint/.MainActivity
Starting: Intent { cmp=com.rescue911.osint/.MainActivity }

$ adb shell pidof com.rescue911.osint
2420
```

After 10 s settle, `com.rescue911.osint` is process 2420 (alive). `dumpsys meminfo` puts our app at ~210 MB RSS — fourth in the process list, behind `system` (305 MB), `systemui` (231 MB), `launcher3` (219 MB). Total of about **10 user-visible processes**, vs ~25 on Android 14 google_apis.

`load average: 6.21 / 2.96 / 1.12` at 2 min uptime — the 1-minute is high because the boot stampede is still tailing off, but the 5/15-minute averages already show the system calming down.

## 9. Screenshot path

`C:\Users\Dmitriy\Projects\911\logs\rescue911_api30_screenshot.png` (89 435 B). Shows the **Cases** screen rendered cleanly, no system overlay:

- Header: "Cases"
- Three case cards with rescue-themed gold status badges:
  - **Anna L.** — Anna Lifshitz · Tel Aviv-Yafo — `INVESTIGATING`
  - **Yossi B.** — Yossi Ben-David · Mount Meron trail — `HUMAN REVIEW`
  - **Dina K.** — Dina Kohen · Haifa central bus station — `OPEN`
- Bottom navigation bar with five tabs, **Cases** highlighted in gold: Cases / Review / Map / Audit / Settings
- Three-button system navigation (back / home / recent) at the bottom (Android 11 default look)

## 10. Logcat path

`C:\Users\Dmitriy\Projects\911\logs\rescue911_api30_logcat.txt` (2 380 866 bytes / ~2.3 MB).

## 11. Is the System UI ANR gone?

**Yes.**

```
$ grep -c "ANR in" logs/rescue911_api30_logcat.txt
0
```

Compare to the previous milestone:
- Pixel_6_API_34 (google_apis), 50 min uptime: 1 ANR in last 5000 lines (had been 30+ at fresh boot)
- Pixel_6_API_34 (google_apis) cold restart with hardened flags: 6 ANRs at 5 min uptime
- Pixel_6_API_34_default (default x86_64) at 30 s settle: 3 ANRs (system_server + 2 systemui)
- **Rescue911_API_30_default at 30 s settle: 0 ANRs**

The "Process system isn't responding" / "System UI isn't responding" overlay seen in every prior screenshot is **not present** here.

## 12. Did Rescue911 crash?

**No.** Crash scan over the entire 2.3 MB logcat:

| Pattern | Hits |
|---|---|
| `FATAL EXCEPTION` | 0 |
| `ANR in com.rescue911.osint` | 0 |
| `Process: com.rescue911.osint` (crash banner) | 0 |
| `Unable to start activity com.rescue911.osint` | 0 |
| `ANR in` (anywhere) | 0 |

## 13. Recommended emulator going forward

**`Rescue911_API_30_default`** — Android 11.0 "R", default (no Google) x86_64, pixel_4 device, 3 GB RAM, 2 cores.

It is the only AVD on the box now (the two unstable API-34 AVDs were removed). The `Pixel_6_API_34*` AVDs can be re-created later from the `system-images;android-34;…` packages still installed under `C:\Android\Sdk\system-images\android-34\` if a Google-API surface or API-34 specific testing is ever needed.

## 14. Exact command

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1
```

The script (`scripts/dev_android_smoke_stable.ps1`) was updated this milestone:
- default `-Avd` is now `Rescue911_API_30_default`
- emulator boot flags reduced to `-memory 3072 -cores 2` to match the lighter image
- header docstring updated

Behavior is unchanged otherwise: it sets `JAVA_HOME` to JDK 19, points `ANDROID_HOME` to `C:\Android\Sdk`, boots the AVD with stable flags if no device is connected, settles, disables animations, installs the APK with auto-uninstall fallback on `INSTALL_FAILED_*`, launches `com.rescue911.osint/.MainActivity`, and **scans for crashes only in our package** — system-level ANRs are reported as a single warning count and never fail the run.

To pin a different AVD or extend the settle:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1 `
    -Avd Rescue911_API_30_default -SettleSec 30
```

Exit codes: `0` healthy, `2` toolchain missing, `3` AVD missing, `4` boot timeout, `5` install failed, `6` app-level crash detected.

---

**No app code changed. No commit. No `git reset`. SDK, project, adb keys, and Docker untouched. SearchAid worktree-deletions still preserved exactly as before.**
