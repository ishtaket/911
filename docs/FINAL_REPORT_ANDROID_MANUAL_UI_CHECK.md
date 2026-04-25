# Manual UI check — Rescue911 vs SystemUI ANR

Date: 2026-04-25
Working directory: `C:\Users\Dmitriy\Projects\911`
Branch: `feat/heat-map-zones`

## Verdict at a glance

| Question | Answer |
|---|---|
| Is the app process alive? | **YES** — `pidof com.rescue911.osint` → `4390`, state `S` (sleeping/idle, normal) |
| Is the app foreground? | **YES** — `topResumedActivity = com.rescue911.osint/.MainActivity` |
| Is there a SystemUI ANR? | **YES** — but it is not in our app; it is in `system_server` and a long list of Google system processes |
| Did `com.rescue911.osint` crash or ANR? | **NO** — `0` matches for FATAL EXCEPTION / `Process: com.rescue911.osint` / `ANR in com.rescue911.osint` / `Unable to start activity` across 37 197 lines of logcat |
| Conclusion | **Emulator/system-process issue, not an app issue.** No app code change needed. |

## 1. App process state

```
$ adb shell pidof com.rescue911.osint
4390

$ adb shell ps -A | grep -E "rescue911|systemui"
u0_a169   767  336  14364028 171776 0  0 S com.android.systemui
u0_a193  4390  336  14042120 109544 0  0 S com.rescue911.osint
```

Both processes are alive and in `S` (interruptible sleep) — the normal idle state for an Android app/service waiting on input.

## 2. Foreground state

```
$ adb shell dumpsys activity activities | grep -E 'topResumedActivity|com.rescue911.osint'
* Task{58fe8b #56 type=standard A=10193:com.rescue911.osint U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
  topResumedActivity=ActivityRecord{3aa2a6f u0 com.rescue911.osint/.MainActivity t56}
  packageName=com.rescue911.osint processName=com.rescue911.osint
  app=ProcessRecord{e68b647 4390:com.rescue911.osint/u0a193}
  Intent { flg=0x10000000 cmp=com.rescue911.osint/.MainActivity }
  baseDir=/data/app/~~aDdzUS5thFstCZ_5xCrigg==/com.rescue911.osint-…/base.apk
```

`am start -n com.rescue911.osint/.MainActivity` after dismissing the dialog returned: `Warning: Activity not started, intent has been delivered to currently running top-most instance.` — i.e. the OS confirmed our activity was already on top.

## 3. SystemUI ANR present?

Yes, several. **None of them are our app.** Pattern `ANR in` over the full logcat (37 197 lines) lists 30+ ANRs across:

```
ANR in com.android.systemui
ANR in com.google.android.gm
ANR in com.google.android.as                       (Android System Intelligence)
ANR in com.android.phone
ANR in com.google.android.bluetooth
ANR in com.google.android.contacts
ANR in com.google.android.apps.messaging
ANR in com.google.android.gms                      (Play services)
ANR in com.google.android.gms.persistent
ANR in com.google.android.googlequicksearchbox:search
ANR in com.google.android.apps.nexuslauncher
ANR in com.google.android.settings.intelligence
ANR in system                                      (system_server itself)
…
```

Pattern `ANR in com.rescue911.osint` → **no matches**.

This is the classic Android emulator "fresh-boot stampede": GMS, Phenotype/Mendel config, the launcher, the assistant, and the system server all wake up at first boot, hit the same SQLite/network endpoints, starve the CPU under the hypervisor, and ANR. The emulator boot earlier reported memory + CPU pressure (`some avg10=46.08`, `some avg10=93.57` from `/proc/pressure`) which is consistent with this picture.

The screenshot shows a **"Process system isn't responding"** dialog — that is `system_server` (the OS scaffolding) ANRing, not `com.android.systemui` and definitely not us. Behind the dialog the **Review** screen of Rescue911 is fully rendered: the "Public sighting on volunteer channel" evidence card with L1/L2/L3 validation dots, an amber "Needs Review" badge, the four review action buttons (Confirm / Reject / Needs more checks / Escalate to authorities), and the bottom nav with Review highlighted in gold (Cases / **Review** / Map / Audit / Settings). The user successfully navigated from the Search Dashboard to Review, proving the UI is interactive.

## 4. Crash check on `com.rescue911.osint`

| Pattern | Hits in our app |
|---|---|
| `FATAL EXCEPTION` | 0 |
| `AndroidRuntime: ***` (crash banner) | 0 |
| `Process: com.rescue911.osint` (in crash context) | 0 |
| `Unable to start activity` | 0 |
| `ANR in com.rescue911.osint` | 0 |

Total mentions of `rescue911.osint` in logcat: **139** — all benign (UI lifecycle, ART startup messages, GC, native loader). Two harmless warnings in our process: `Unexpected CPU variant for x86: x86_64` (ART note on x86_64 emulator) and `Unable to open '…/base.dm'` (optional DEX metadata, not required).

## 5. Screenshot path

`C:\Users\Dmitriy\Projects\911\logs\android_manual_ui_check_screenshot.png` — 157 KB. Shows the Review screen rendered underneath the system ANR dialog. (See section 3 for what's visible.)

## 6. Logcat path

`C:\Users\Dmitriy\Projects\911\logs\android_manual_ui_check_logcat.txt` — 37 197 lines from `adb logcat -d` taken after dismissing the dialog and re-fronting the activity.

## 7. Docker / backend status (for clarity)

```
$ docker ps
bissnesman-api-1       Up 18 minutes
bissnesman-redis-1     Up 1 hour (healthy)
bissnesman-postgres-1  Up 1 hour (healthy)

$ docker compose -f infra/docker-compose.local.yml ps
NAME   IMAGE   COMMAND   SERVICE   CREATED   STATUS   PORTS
(no rows — no Rescue911 services running)
```

Docker is healthy. Three containers are up for an unrelated project (`bissnesman-*`) — not Rescue911. The Rescue911 compose stack (`rescue911-local`) has no services running.

**Docker is not required for this UI check.** The Android app's DI (`android/app/src/main/java/com/rescue911/osint/core/di/AppModule.kt`) binds `Rescue911Repository` to **`MockRescue911Repository`** — the app reads from in-process mock data, makes no network calls, and would not be affected by the backend being up or down.

## 8. Conclusion: app issue vs emulator issue

**Emulator/system-process issue.** Specifically:

- The ANR dialog the user is seeing is raised by the emulator's `system_server` (or `com.android.systemui`), which itself is being starved by the wave of GMS / Mendel / Phenotype / Launcher / Assistant work that fires on a fresh cold boot of an `android-34;google_apis;x86_64` image with only 2 GB of guest RAM (the emulator log line `Increasing RAM size to 2048MB` confirms that).
- The Rescue911 process is alive, foreground, and the UI is rendered. The "Confirm" button on Review even shows the pressed state in the screenshot, suggesting the user touched it before the dialog popped.
- No code, no DI binding, no resource, no permission, no manifest entry in `com.rescue911.osint` is involved in any ANR or crash record in logcat.

## 9. Next recommended action

In order of cost. Pick (a) for fastest, (c) for most durable.

**(a) Just dismiss "Wait" or `KEYCODE_BACK` and keep using the app.** The app behind the dialog is alive and responsive. The system processes typically settle within 1–3 minutes after a fresh boot.

```powershell
& "C:\Android\Sdk\platform-tools\adb.exe" shell input keyevent KEYCODE_BACK
```

**(b) Cold-restart the emulator with more guest RAM** (the host has plenty — `49 GB` free on `C:`, and Windows 11 likely has 8+ GB unallocated). 4 GB / 4 cores is much friendlier for `google_apis` images:

```powershell
# stop:
& "C:\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 emu kill

# restart with more RAM, more cores, no audio, cold boot:
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Start-Process "C:\Android\Sdk\emulator\emulator.exe" `
    -ArgumentList "-avd","Pixel_6_API_34","-no-snapshot-load","-no-audio","-no-boot-anim","-memory","4096","-cores","4" `
    -WindowStyle Hidden
```

**(c) Switch to the non-Google system image** to drop GMS/Phenotype/Assistant entirely. This eliminates the largest source of fresh-boot ANRs at the cost of no Google sign-in / Play services in the emulator (irrelevant for our app, which talks only to our own backend):

```powershell
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "system-images;android-34;default;x86_64"
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n Pixel_6_API_34_default -k "system-images;android-34;default;x86_64" -d pixel_6 --force
```

(Creates a *new* AVD named `Pixel_6_API_34_default`. The existing `Pixel_6_API_34` AVD is left alone.)

**No app change is recommended.** The app is healthy.

---

*No code changed, no APK rebuilt, no AVD deleted, no commit, no `git reset`. SearchAid worktree-deletions remain untouched.*
