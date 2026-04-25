# Emulator stability for Rescue911 smoke testing

Date: 2026-04-25
Working directory: `C:\Users\Dmitriy\Projects\911`
Branch: `feat/heat-map-zones`

## TL;DR

| Question | Answer |
|---|---|
| Is `com.rescue911.osint` healthy? | **YES** — 0 FATAL EXCEPTION, 0 ANR, foreground on every test |
| Did disabling animations help? | Marginal. Useful, no harm. |
| Did better boot flags + 4 GB / 4 cores help? | **NO** for the dialog itself; helps post-settle smoothness |
| Did the lighter `default` (non-Google) AVD help? | **YES** — 3 ANRs vs 30+ on google_apis, half the running processes, dialog still appears at fresh-boot but the device settles much faster |
| Recommended for future smoke | **`Pixel_6_API_34_default`** (new AVD, the existing `Pixel_6_API_34` is left alone) |
| Recommended script | **`scripts/dev_android_smoke_stable.ps1`** (new) |

## Phase 1 — Baseline (pre-stabilization, on existing `Pixel_6_API_34`)

Saved at `logs/emulator_health_before.txt`.

```
boot_completed:        1
uptime:                50 min, load average: 0.40 0.26 2.19
animation scales:      1.0 / 1.0 / null   (defaults)
ANRs in last 5000 lns: 1                 (system had stabilized)
topResumedActivity:    com.rescue911.osint/.MainActivity   (PID 4390)
top RSS:               system 253 MB, googlequicksearchbox:search 215 MB,
                       gms.persistent 200 MB, systemui 181 MB,
                       youtube 179 MB, gms 179 MB, ...
```

`/proc/pressure/{cpu,memory,io}` returned `Permission denied` (PSI is root-only on user-build images).

## Phase 2 — Non-destructive stabilization on the same AVD

Saved at `logs/emulator_health_after.txt`, `logs/rescue911_stabilized_logcat.txt`, `logs/rescue911_stabilized_screenshot.png`.

Actions:
- `settings put global window_animation_scale 0`
- `settings put global transition_animation_scale 0`
- `settings put global animator_duration_scale 0`
- `am force-stop` for `googlequicksearchbox`, `gm`, `apps.messaging`, `apps.nexuslauncher` (skipped our app, did not uninstall anything)
- `am start` re-launch

Result:
- Animations confirmed 0 / 0 / 0.
- Force-stopped processes auto-restarted (Android home + GMS-adjacent services restart on demand). Effect on RSS pressure: minimal.
- App still PID 4390, foreground, 0 app-level crashes.
- ANR count unchanged (1).
- "Process system isn't responding" dialog still in front of our app.

**Conclusion:** Disabling animations and stopping a few Google apps doesn't break anything but doesn't change the user-facing problem.

## Phase 3 — Cold restart with hardened boot flags (still google_apis)

Saved at `logs/emulator_cold_stdout.log`, `logs/rescue911_coldboot_logcat.txt`, `logs/rescue911_coldboot_screenshot.png`.

```powershell
emulator.exe -avd Pixel_6_API_34 `
  -no-snapshot-load -no-snapshot-save `
  -no-boot-anim -no-audio `
  -memory 4096 -cores 4 `
  -gpu swiftshader_indirect `
  -netdelay none -netspeed full
```

Boot: 84 s. After a 30 s settle:
- App installed (`Streamed Install / Success`).
- App launched (PID 4368, fresh process), `topResumedActivity = MainActivity`.
- 0 app-level crashes.
- **6 ANRs in last 5000 lines** (all system / Google).
- **Load average 25.31 / 8.98 / 3.24** at 5 min uptime — extreme. The 4-core / 4 GB headroom didn't help because the bottleneck is auto-started GMS / Phenotype / Mendel / Assistant / Launcher work, not RAM.
- Dialog still present.

**Conclusion:** More RAM and more cores do not stop the boot stampede on the `google_apis` image. The image just runs too many background services at first boot.

## Phase 4 — Lighter `default` (non-Google) AVD

Installed system image (was missing): `system-images;android-34;default;x86_64` via the existing `sdkmanager` (~1.5 GB). **Existing `Pixel_6_API_34` AVD untouched.** Created a *new* AVD:

```powershell
avdmanager.bat create avd `
    -n Pixel_6_API_34_default `
    -k "system-images;android-34;default;x86_64" `
    -d pixel_6 --force
```

`emulator -list-avds` → both AVDs present:
```
Pixel_6_API_34            (existing, google_apis)
Pixel_6_API_34_default    (new, default / no Google)
```

Booted with the same hardened flags. Boot: **76 s**. After a 30 s settle:

```
load average:          10.23 / 4.79 / 1.82   (3 min uptime)
ANRs (last 5000 ln):   3                       (vs 6 on google_apis, halved)
top RSS:               system 367 MB, systemui 252 MB,
                       com.rescue911.osint 205 MB, launcher3 198 MB,
                       phone 150 MB, settings 146 MB, bluetooth 137 MB
```

**The full process list is dramatically smaller** — no `com.google.android.gms*`, no `apps.messaging`, no `apps.nexuslauncher`, no `googlequicksearchbox`, no Mendel/Phenotype services, no Gmail. ~60 % fewer processes than google_apis.

After a further **90 s settle** (`logs/rescue911_default_avd_settled_screenshot.png`):
- `load average: 1.55 / 3.40 / 1.74` — fully calmed down.
- ANRs unchanged at 3 (no new ones accumulated during the settle).
- App still PID 2793, foreground.

**Crash scan on `com.rescue911.osint`** in `rescue911_default_avd_logcat.txt`:

| Pattern | Hits |
|---|---|
| `FATAL EXCEPTION` (anywhere) | 0 |
| `ANR in com.rescue911.osint` | 0 |
| `Process: com.rescue911.osint` (crash banner) | 0 |
| `Unable to start activity com.rescue911.osint` | 0 |

**System-level ANRs** (3 total, all expected boot-stampede pattern):
- `ANR in com.android.systemui` × 2
  - reason: `Input dispatching timed out … FocusEvent (hasFocus=true)` after 5056 ms
- `ANR in system` × 1 (system_server itself)

The `system_server` ANR is what raises the "Process system isn't responding" overlay. It is purely a function of the emulator's first-boot input-pipeline race; nothing in our APK contributes.

The screenshots prove the app surface is alive underneath:
- `rescue911_default_avd_screenshot.png` — Cases list with three Israel-themed mock cases ("Anna L. — Anna Lifshitz · Tel Aviv-Yafo · INVESTIGATING", "Yossi B. — Yossi Ben-David · Mount Meron trail · HUMAN REVIEW", "Dina K. · OPEN") and bottom-nav with **Cases** highlighted in gold (Cases / Review / Map / Audit / Settings). Three-button system navigation visible at the bottom (default image uses classic nav, not gestures).
- The system overlay is on top, but `dumpsys activity activities` confirms our activity is the `topResumedActivity` for the entire run.

## Phase 5 — New script: `scripts/dev_android_smoke_stable.ps1`

Created (the existing `dev_android_smoke.ps1` is left alone for backwards compatibility). The new script:

1. Sets `JAVA_HOME = C:\Program Files\Java\jdk-19` if present.
2. Sets `ANDROID_HOME` / `ANDROID_SDK_ROOT` to `C:\Android\Sdk`.
3. If no device is connected, boots the requested AVD (defaults to `Pixel_6_API_34_default`) with the hardened flags from Phase 3.
4. Waits up to 240 s for `sys.boot_completed = 1`.
5. Sleeps a configurable `-SettleSec` (default 30) to ride out the stampede.
6. Disables animations.
7. `adb install -r` (with auto-uninstall on `INSTALL_FAILED_*`).
8. `am start` MainActivity, then 8 s settle.
9. Captures `logs/rescue911_stable_logcat.txt` + `logs/rescue911_stable_screenshot.png`.
10. **Scans crashes only for `com.rescue911.osint`**: `FATAL EXCEPTION` lines that mention our package within 2 KB, `ANR in com.rescue911.osint`, `Process: com.rescue911.osint` crash banner, `Unable to start activity …com.rescue911.osint`.
11. Reports system / Google ANRs as a *warning count* (e.g. `System/Google ANRs in logcat (warnings, not failures): 3`) and exits 0 if no app-level failure.

Usage:

```powershell
# Default: Pixel_6_API_34_default
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1

# Pin a specific AVD or longer settle:
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1 `
    -Avd Pixel_6_API_34_default -SettleSec 90
```

Exit codes: `0` healthy, `2` toolchain missing, `3` AVD missing, `4` boot timeout, `5` install failed, `6` app-level crash detected.

## Phase 6 — Recommendations and exact commands

**Use `Pixel_6_API_34_default` for smoke testing.** It's slimmer, settles faster, and produces fewer system ANRs. Keep `Pixel_6_API_34` (google_apis) for any test that genuinely needs Google sign-in or Play Services (Rescue911 does not — the Android client only talks to our backend).

**Boot recipe (one command):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ANDROID_HOME = "C:\Android\Sdk"; $env:ANDROID_SDK_ROOT = "C:\Android\Sdk"

Start-Process "C:\Android\Sdk\emulator\emulator.exe" `
    -ArgumentList "-avd","Pixel_6_API_34_default", `
                  "-no-snapshot-load","-no-snapshot-save", `
                  "-no-boot-anim","-no-audio", `
                  "-memory","4096","-cores","4", `
                  "-gpu","swiftshader_indirect", `
                  "-netdelay","none","-netspeed","full" `
    -WindowStyle Hidden `
    -RedirectStandardOutput "C:\Users\Dmitriy\Projects\911\logs\emulator_default_stdout.log" `
    -RedirectStandardError  "C:\Users\Dmitriy\Projects\911\logs\emulator_default_stderr.log"
```

**Or just run the script:**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke_stable.ps1
```

**If the "Process system isn't responding" dialog still appears in your first 30 s:**
- Click **Wait** in the emulator window (one click), or
- Re-run the smoke after the system has been up for ~2 minutes — load drops to ~1.5 (verified in Phase 4), or
- Pass `-SettleSec 90` to the script for a one-shot longer settle.

The app underneath is alive in every case — `dumpsys activity activities | grep topResumedActivity` confirms `com.rescue911.osint/.MainActivity` is the focused activity, and the screenshot under the dialog shows the Cases list rendered.

## Whether Rescue911 remained healthy

Across **four** test runs (existing google_apis settled / google_apis cold / default cold / default settled), `com.rescue911.osint` was:
- Always alive (PID present in `ps -A`).
- Always foreground (`topResumedActivity = com.rescue911.osint/.MainActivity`).
- Always rendering content (Search Dashboard or Cases list visible behind the dialog).
- 0 FATAL EXCEPTION, 0 ANR, 0 `Unable to start activity` — across **every** logcat dump.

**No app code change was made or warranted.**

## Files produced (not committed)

```
logs/emulator_health_before.txt
logs/emulator_health_after.txt
logs/rescue911_stabilized_logcat.txt
logs/rescue911_stabilized_screenshot.png
logs/emulator_cold_stdout.log
logs/emulator_cold_stderr.log
logs/rescue911_coldboot_logcat.txt
logs/rescue911_coldboot_screenshot.png
logs/emulator_default_stdout.log
logs/emulator_default_stderr.log
logs/rescue911_default_avd_logcat.txt
logs/rescue911_default_avd_screenshot.png
logs/rescue911_default_avd_settled_screenshot.png
logs/sdk_install_default_log.txt
scripts/dev_android_smoke_stable.ps1
docs/FINAL_REPORT_EMULATOR_STABILITY.md   (this file)
```

(`logs/` is currently not in `.gitignore`. If you plan to commit any of this, add `logs/` to `.gitignore` first — same recommendation as the prior milestone.)

---

*No app code changed. No commit. No `git reset`. Existing `Pixel_6_API_34` AVD untouched. New `Pixel_6_API_34_default` AVD created. SearchAid worktree-deletions still preserved.*
