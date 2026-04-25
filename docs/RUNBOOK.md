# Runbook

## Local dev (Windows)

| Goal | Command |
| --- | --- |
| Check prereqs | `.\scripts\check_local_environment.ps1` |
| Install missing tools | `.\scripts\install_missing_prereqs.ps1` |
| Validate scaffold | `.\scripts\verify_project_scaffold.ps1` |
| Bring up infra | `.\scripts\dev_docker_up.ps1` |
| Tear down infra | `.\scripts\dev_docker_down.ps1` |
| Run backend | `.\scripts\dev_backend.ps1` |
| Backend tests | `.\scripts\dev_backend_test.ps1` |
| Android build | `.\scripts\dev_android_build.ps1` |
| Android install | `.\scripts\dev_android_install.ps1` |
| Android smoke | `.\scripts\dev_android_smoke.ps1` |
| Combined env+scaffold+backend | `.\scripts\dev_all_check.ps1` |

## Common issues

### Docker daemon not running
`dev_docker_up.ps1` returns exit 3. Start Docker Desktop, then re-run.

### Java version mismatch
AGP 8.5 expects JDK 17+. JDK 19 works. JDK 11 will fail with toolchain errors.

### Android SDK missing
`ANDROID_HOME` / `ANDROID_SDK_ROOT` must be set. After install:
```powershell
setx ANDROID_HOME "C:\Android\Sdk"
# restart terminal
```

### Gradle wrapper jar missing
`dev_android_build.ps1` will download Gradle 8.10.2 and run `gradle wrapper` automatically.
After first successful run, `gradlew.bat` works directly.

### `adb devices` empty
Start the emulator (`emulator -list-avds` then `emulator -avd <name>`) or plug in a device with USB debugging enabled.

## Backend operations

- Health: `GET /v1/health`
- Provider status: hit each `/v1/search/start/<case_id>` and inspect the audit log (`GET /v1/audit`) for `provider_call` / `provider_error` entries.
- Reset in-memory store: restart the backend (milestone 1 uses an in-memory store; milestone 2 will use Postgres).

## Logs

- Backend: stdout / stderr (uvicorn). In Docker, `docker logs rescue911-backend`.
- Android: `adb logcat`.
- Audit: `GET /v1/audit?limit=200`.
