# Milestone — Git safety + Android APK build

Date: 2026-04-25
Working directory: `C:\Users\Dmitriy\Projects\911`
Branch: `feat/heat-map-zones`

## 1. `.gitignore` status

- At start: **deleted in worktree** (the file was tracked in the index but missing on disk; `git status` showed ` D .gitignore`).
- Restored with `git restore .gitignore` (49 lines from index unchanged).
- Extended with new rules; the additions are unstaged. No commit was made.

Added rules (appended after the existing `# Claude` block):

```gitignore
# Env / secrets
.env
.env.*
!.env.example

# Python
.venv/
venv/
__pycache__/
*.pyc
*.pyo
backend/.venv/
backend/.pytest_cache/
.pytest_cache/

# Node
node_modules/

# Build output
dist/
```

Verified live with `git check-ignore -v`:

| Path | Ignored by |
|---|---|
| `backend/.venv/Scripts/python.exe` | `.gitignore:62 backend/.venv/` |
| `backend/.pytest_cache` | `.gitignore:64 .pytest_cache/` |
| `.env` | `.gitignore:52 .env` |
| `.env.local` | `.gitignore:53 .env.*` |
| `.env.example` | **not ignored** (negation `.gitignore:54 !.env.example` works) |
| `android/app/build` | `android/.gitignore:3 **/build/` |
| `android/.gradle` | `android/.gitignore:1 .gradle/` |

`git status -- .gitignore` → ` M .gitignore` (modified, **not staged, not committed**).

## 2. Android SDK location

`C:\Android\Sdk` — already installed from the previous session, untouched.

```
C:\Android\Sdk\
├── cmdline-tools\latest\   (sdkmanager present)
├── platform-tools\         (adb.exe)
├── platforms\android-34\   (compileSdk target)
├── build-tools\34.0.0\
└── licenses\
```

`ANDROID_HOME` and `ANDROID_SDK_ROOT` both set at User scope and visible in this session.

## 3. Java version and `JAVA_HOME`

- `java` on PATH → JRE **1.8.0_351** (Oracle javapath default — incompatible with Android Gradle Plugin 8.5.x).
- `JAVA_HOME` env var: **unset** at session level.
- JDK **19** is installed at `C:\Program Files\Java\jdk-19` (with `bin\javac.exe` present).
- The build script (`scripts/dev_android_build.ps1`) auto-detects this JDK and prepends `$JAVA_HOME\bin` to PATH for the build session, so the user does not need to set `JAVA_HOME` manually for `assembleDebug` to work.
- Recommendation (optional, for IDE/Gradle invoked outside the script): `setx JAVA_HOME "C:\Program Files\Java\jdk-19"`.

## 4. Gradle wrapper status

- `android/gradlew.bat` — **present**.
- `android/gradle/wrapper/gradle-wrapper.jar` — **present** (untracked file, ignored by `.gitignore` rule `/android/gradle/wrapper/gradle-wrapper.jar`).
- No bootstrap step needed.

## 5. Android build result

`scripts/dev_android_build.ps1` → **BUILD SUCCESSFUL in 42 s** (`41 actionable tasks: 41 up-to-date`).

First attempt failed with a PowerShell parser error caused by an em-dash (`—`, U+2014) in a `Write-Host` string at line 36 of the build script: PowerShell 5.1 reads UTF-8-without-BOM .ps1 files as the legacy code page, mangling the bytes into a sequence containing a stray `"` that prematurely terminates the string literal. Fixed by replacing the em-dash with `--` (one-character ASCII edit). Retry succeeded.

## 6. APK path

`Test-Path` confirmed:

```
C:\Users\Dmitriy\Projects\911\android\app\build\outputs\apk\debug\app-debug.apk
```

Size: **17.4 MB** (18 217 446 bytes), mtime 2026-04-25.

## 7. Android unit test result

`.\gradlew.bat :app:testDebugUnitTest` → **BUILD SUCCESSFUL in 4 s** (`30 actionable tasks: 30 up-to-date`, results cached from prior identical inputs).

JUnit XML reports under `android/app/build/test-results/testDebugUnitTest/`:

| Suite | tests | failures | errors | skipped |
|---|---|---|---|---|
| `com.rescue911.osint.data.mock.MockDataTest` | 4 | 0 | 0 | 0 |
| `com.rescue911.osint.domain.model.ValidationTest` | 3 | 0 | 0 | 0 |
| **Total** | **7** | **0** | **0** | **0** |

HTML report: `android/app/build/reports/tests/testDebugUnitTest/index.html`.

## 8. ADB / emulator status

- `adb devices` → daemon started, `List of devices attached` (empty).
- No physical device connected; no emulator running.
- `scripts/dev_android_install.ps1` and `scripts/dev_android_smoke.ps1` were **not run** (no target).
- Emulator binary is not installed on this machine (per `check_local_environment.ps1` WARN).

## 9. Backend regression result

`backend/.venv/Scripts/python.exe -m pytest -q` → **19 passed** (single `[100%]` line of dots; one harmless `pytest-asyncio` deprecation warning about an unset `asyncio_default_fixture_loop_scope`, no actual test issues).

Note: `scripts/dev_backend_test.ps1` runs the same command but PowerShell 5.1 treats the deprecation warning that pytest writes to stderr as a `NativeCommandError`, returning exit-code-1 to the host even though pytest itself exited 0. The tests pass — the script's exit code is misleading. Cosmetic fix: drop `2>&1` from the script or set `$ErrorActionPreference = 'Continue'`. Not done in this milestone (out of scope).

## 10. Remaining blockers

| # | Blocker | Severity | Path forward |
|---|---|---|---|
| 1 | No emulator / no physical device — cannot run instrumented Compose UI tests or smoke install | **low** | Install emulator + system image (~2 GB), or attach a USB device |
| 2 | `JAVA_HOME` not persisted at User scope — only the build script handles it | **low** | Optional: `setx JAVA_HOME "C:\Program Files\Java\jdk-19"` |
| 3 | `dev_backend_test.ps1` returns exit 1 despite green tests, due to PowerShell stderr wrapping | **trivial** | Cosmetic; tests are green via the direct pytest command |
| 4 | DB persistence not wired (in-memory store) — milestone 2 item | medium | SQLAlchemy ORM + Alembic |
| 5 | No auth / RBAC on backend routes — milestone 2 item | medium | Bearer token + role check; lock down `/v1/audit`, `/v1/review` |
| 6 | Backend CORS `allow_origins=["*"]` — fine for local; tighten in staging/prod | low | Reverse proxy in `infra/nginx/staging.conf` already restricts at the edge |
| 7 | `pytest-asyncio` deprecation warning | trivial | Set `asyncio_default_fixture_loop_scope = "function"` in `pyproject.toml` |
| 8 | `datetime.utcnow()` deprecation in backend services | trivial | Replace with `datetime.now(datetime.UTC)` |
| 9 | Other PS scripts may contain non-ASCII (e.g. `≥` in `verify_project_scaffold.ps1`, `→` in a comment in `dev_android_smoke.ps1`) | low | Cosmetic — `≥` is inside non-critical strings, `→` is inside a comment; both run today, but PS 5.1 may print mojibake |

## 11. Exact next commands

Sanity check the protection (run any time):

```powershell
git status --short -- .gitignore
git check-ignore -v backend\.venv\Scripts\python.exe .env .env.local .env.example
```

Re-build the APK from a clean state (forces actual compilation, not cached):

```powershell
Set-Location C:\Users\Dmitriy\Projects\911\android
.\gradlew.bat :app:clean :app:assembleDebug --console=plain
```

Re-run unit tests forcing actual execution:

```powershell
Set-Location C:\Users\Dmitriy\Projects\911\android
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
```

Backend regression (the green path):

```bash
backend\.venv\Scripts\python.exe -m pytest -q
```

When you are ready to install the APK on a device or emulator:

```powershell
# Confirm a device is attached:
& "C:\Android\Sdk\platform-tools\adb.exe" devices

# Then:
powershell -ExecutionPolicy Bypass -File scripts\dev_android_install.ps1
powershell -ExecutionPolicy Bypass -File scripts\dev_android_smoke.ps1
```

If you want to install the emulator (~2 GB on `C:`):

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --install "emulator" "system-images;android-34;google_apis;x86_64"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd -n Pixel_6_API_34 -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList "-avd","Pixel_6_API_34","-no-snapshot"
```

Optional: persist `JAVA_HOME` so non-script Gradle invocations (Android Studio, IDE) pick it up:

```powershell
setx JAVA_HOME "C:\Program Files\Java\jdk-19"
```

---

**Summary: the worktree is now safe from accidental bad commits, and the Android debug APK builds and the unit tests pass.** No git commit, no `git reset`, no destructive action was taken in this milestone.
