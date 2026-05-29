# Personal Context Assistant (PCA)

An Android app that listens to the owner's day in five-minute windows,
transcribes locally with whisper.cpp, identifies the owner's voice via
an ECAPA-TDNN ONNX embedding, anonymises PII, and asks a
subscription-CLI LLM (codex / Claude Code / Gemini CLI) whether the
window contains anything worth surfacing or remembering. Built for a
Samsung Galaxy S21 Ultra (SM-G998B/DS, Exynos 2100, Android 14) but
runs on any arm64 Android 13+ device.

```
mic → AudioCapture → Whisper STT
                   → ECAPA owner-ID
                   → SQLCipher transcript store
                   → Anonymizer (PII → tokens)
                   → LLM (codex CLI / Gemini CLI / mock)
                   → Advice notification, open-threads, hour/day rollups
```

## Download the APK directly to your phone

No GitHub login needed. The latest debug build is auto-published from
the development branch:

* APK — <https://github.com/ishtaket/911/releases/download/pca-latest/app-debug.apk>
* SHA-256 — <https://github.com/ishtaket/911/releases/download/pca-latest/app-debug.apk.sha256>
* Build info — <https://github.com/ishtaket/911/releases/download/pca-latest/BUILD_INFO.txt>

Open the APK link in Chrome on the phone, allow "install unknown
apps" for Chrome once when prompted, and tap install. The
`whisper.cpp` and ECAPA-TDNN ONNX models download on first launch
over WiFi.

## Hard safety boundaries

* No raw PCM is ever persisted. Audio is forwarded to STT and dropped.
* Local DB is encrypted with SQLCipher; the passphrase is wrapped by
  an AndroidKeystore AES/GCM key (StrongBox preferred).
* The Anonymizer runs before any transcript leaves device memory.
  Owner identity (email, phone) never appears in an LLM payload.
* LLM CLIs use **subscription auth** (the CLI's own browser-OAuth
  flow), never pay-per-token API keys. The Android side carries no
  `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, or `GEMINI_API_KEY`.
* The HTTP bridge listens on `127.0.0.1` by default; cleartext HTTP
  is allowed only on loopback and RFC-1918 ranges via
  `network_security_config.xml`.

## Repository layout

```
.
├── pca-android/             # Kotlin/Compose app (com.pca.assistant)
│   ├── app/                 # Android module + tests
│   ├── bridge/              # Python HTTP bridge to codex / Claude / Gemini CLI
│   └── ...
├── .github/workflows/       # CI (pca-ci.yml) + repo safety
├── CLAUDE.md                # Project operating system for Claude Code
└── README.md
```

## Build locally

JDK 17 + Android SDK with platform 34 + NDK 26.1.10909125 + CMake 3.22.

```bash
cd pca-android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The resulting APK is at
`pca-android/app/build/outputs/apk/debug/app-debug.apk`.

## Bridge (subscription CLI access)

The Android app POSTs a `LlmRequest` JSON to a user-controlled HTTP
bridge that fronts an installed CLI (`codex`, `claude`, or `gemini`).
A reference Python bridge lives at `pca-android/bridge/server.py`,
and `pca-android/bridge/install-termux.sh` runs it on the same phone
under Termux. Authentication is handled by the CLI's own browser
login flow — no API keys are stored on the device.

## CI / branch model

* Develop on `claude/build-samsung-app-YaU0S`.
* Every push to that branch triggers `pca-ci.yml`, which builds the
  debug APK, runs JVM unit tests, and refreshes the rolling
  `pca-latest` GitHub Release with the new APK + SHA-256.
* `repo-safety.yml` blocks tracked APKs, AABs, binary leaks outside
  `pca-android/app/src/main/res/`, and pattern-matched API keys.
