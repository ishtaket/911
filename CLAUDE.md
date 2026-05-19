# CLAUDE.md — Project Operating System

You are Claude Code working inside this repository.

## Project

**Personal Context Assistant (PCA)** — an Android app that listens to
the owner's day in five-minute windows, transcribes locally with
whisper.cpp, identifies whether the owner is speaking via an
ECAPA-TDNN ONNX embedding, anonymises PII out of the transcript, and
asks a subscription-CLI LLM (codex / Claude Code / Gemini CLI) whether
the window contains anything worth surfacing as advice or remembering
as an open thread. Built for a Samsung Galaxy S21 Ultra (SM-G998B/DS,
Exynos 2100, Android 14) but works on any arm64 Android 13+ device.

All code lives under `pca-android/`.

## Architecture

```
mic → AudioCapture (16 kHz mono PCM, 30 s buffers, never persisted)
        ↓
      VoiceActivityDetector → WhisperJniRecognizer (whisper.cpp JNI)
                            → OnnxEcapaIdentifier (owner vs guest)
        ↓
   TranscriptDao (Room + SQLCipher) → WindowAggregator → WindowProcessor
        ↓
  Anonymizer (regex PII → tokens) → ProviderRouter (codex | gemini | bridge | mock)
        ↓
  LlmDecision JSON → InterventionDao + OpenThreadDao + AdviceNotifier
        ↓
  HourRollup → DayRollup → ProfileRollup  (WorkManager)
```

Subscription-CLI LLM access happens via a small user-controlled HTTP
bridge (Termux on the same phone, or a home server). The Android
device never talks directly to an LLM API and never carries an API
key. A deterministic `MockLocalProvider` keeps the app usable before
the bridge is set up.

## Hard safety boundaries

- No raw PCM is ever persisted. Audio buffers are forwarded to STT and
  then dropped.
- DB is encrypted at rest with SQLCipher; the passphrase is wrapped by
  an AndroidKeystore AES/GCM key (StrongBox preferred).
- Anonymizer always runs before the transcript leaves device memory.
  Owner identity (email, phone) MUST NOT appear in any LLM payload.
- LLM CLIs are reached over **subscription auth** (the CLI's own
  browser-OAuth flow), never pay-per-token API keys. Do not introduce
  `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` env vars
  into the Android side or the bridge.
- The HTTP bridge listens only on `127.0.0.1` by default; cleartext
  HTTP is permitted only for loopback + RFC-1918 ranges via
  `network_security_config.xml`.
- WindowProcessor caps runaway LLM output (max 32 new threads, 4096
  advice chars, 1024 reason chars, 900-row SQLite IN-list chunking).
- ModelDownloader refuses non-https, non-LAN URLs; verifies SHA-256
  when the spec carries one.

## Stack

- **Android 14**, Kotlin 2.0, Jetpack Compose, Material 3, Hilt,
  Room + SQLCipher, DataStore, OkHttp, Retrofit, WorkManager.
- **whisper.cpp v1.7.1** fetched via CMake FetchContent at build time
  (NEON arm64-v8a). No QNN — Exynos 2100 has no Hexagon DSP.
- **ECAPA-TDNN ONNX** loaded via ONNX Runtime Android. A bootstrap
  WorkManager job auto-downloads the model on first install when the
  device is on UNMETERED network with battery and storage not low.
- **kotlinx.serialization** for the LLM wire contract.
- JUnit 4 + mockk + Robolectric for unit tests. CI runs
  `:app:testDebugUnitTest` + `:app:assembleDebug` on every push and
  PR.

## Branch + release model

- Develop on `claude/build-samsung-app-YaU0S`.
- `claude/build-samsung-app-YaU0S` is the only branch the rolling
  `pca-latest` GitHub Release auto-publishes from (see
  `.github/workflows/pca-ci.yml`).
- The release exposes `app-debug.apk`, its SHA-256, and a build-info
  text file for direct phone download — no GitHub login required.

## Validation rule

Every change must include:
1. Unit tests covering new logic.
2. Mock-bridge or fake-DAO coverage where the change crosses a process
   or storage boundary.
3. A line in the PR description explaining the privacy impact (does
   any new data leave the device? does it touch the LLM payload?).
4. CI passing — both `pca-android · assembleDebug + unit tests` and
   `Repository safety checks` must be green.

## Execution rules

- Inspect the repository before changing code; do not assume
  structure.
- Keep changes small, testable, documented.
- Prefer working mocks over waiting for API keys.
- Prefer minimal working skeleton over over-engineered broken
  structure.
- Do not delete user files outside `pca-android/`.
- Do not commit secrets.
- Do not claim tests passed unless actually run.
- If blocked, document the blocker in a PR comment and continue.
