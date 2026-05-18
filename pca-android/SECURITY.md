# PCA — Threat model and security properties

This document is the authoritative reference for how Personal Context
Assistant (PCA) handles confidentiality, integrity, and availability of
the always-on audio + LLM pipeline described in
`docs/spec` / the Android Assistant Spec. It is intentionally specific —
each property is mapped back to the file or commit that enforces it, and
every mitigation has a stable identifier (`PCA-S-N`) so audit reports and
follow-up tests can reference them.

The audience is: security reviewers, third-party integrators of the
HTTP bridge, and the owner deciding whether to enable continuous
listening on their phone.

---

## 1. Assets

| Asset | Sensitivity | Where it lives |
|-------|-------------|----------------|
| Raw microphone PCM         | High — ambient conversations | RAM only; **never persisted** (spec §3.1, §7.2) |
| Speech transcripts         | High — verbatim utterances   | `transcripts` table, SQLCipher-encrypted |
| Anonymisation token map    | Critical — links tokens to PII | RAM only, scoped to one LLM request |
| Owner voice embedding      | High — biometric identifier  | `owner_profile.voice_embedding` BLOB, SQLCipher-encrypted; never leaves the device |
| Window context (location, owner-presence, anonymised text) | Medium | `windows` table, SQLCipher-encrypted; sent to LLM in anonymised form |
| Open threads / day narrative / L3 profile | Medium — derived identity | `open_threads`, `day_summaries`, `owner_profile.l3_summary`; SQLCipher-encrypted |
| LLM advice notifications   | Medium | `interventions` table + Android notification surface |
| Model weights (Whisper, ECAPA) | Low directly, **High by code-equivalence** (loaded into native runtimes) | App-private `filesDir/models/`; downloaded over TLS |
| SQLCipher passphrase       | Critical — DB master key      | Random 32 bytes, wrapped by an Android Keystore AES/GCM key (StrongBox when present), ciphertext in SharedPreferences |
| Bridge URL / API tokens    | Out of scope (user-controlled, never logged)               | DataStore |

## 2. Trust boundaries

```
   ┌──────────────────── Android process (PCA) ─────────────────────┐
   │                                                                │
   │  mic → AudioCapture → VAD → STT (whisper.cpp JNI)              │
   │                ↓                                               │
   │   per-chunk transcripts → SQLCipher (Room)                     │
   │                ↓                                               │
   │   WindowAggregator ── anonymise → window payload               │
   │                          │           ↓ HTTPS / LAN cleartext   │
   │                          │      ┌──── bridge (codex / Gemini)──┐
   │                          │      │  out of scope; user-hosted   │
   │                          │      └──────────────────────────────┘
   │                          ↓                                     │
   │             token map (RAM) ── drop after request              │
   │                                                                │
   └────────────────────────────────────────────────────────────────┘

   Crypto root of trust: Android Keystore (HW-backed when StrongBox is present)
```

The trust boundary is the process itself. The OS (Android), the kernel,
Keystore, the SoC, and the SQLCipher implementation are all in the TCB.
A compromised OS or root user is **out of scope**.

## 3. Threats and mitigations

| Threat | Mitigation | ID | Where |
|--------|------------|----|-------|
| Passive eavesdropping on conversation | Raw audio never persists; transcripts are local; only anonymised text leaves the device | `PCA-S-0` | `ListeningService.onSpeechChunk`, spec §7.2 |
| **Token collision across chunks** correlates PII for the LLM | Anonymisation happens at **window-build time** over the joined raw text — one coherent token map per window, mapping discarded at the end of the request | `PCA-S-1` | `WindowAggregator.collectSlice`, regression test in `AnonymizerCollisionRegressionTest` |
| Cleartext exfil of bridge JSON to an unintended public host | Code-layer enforcement: `HttpBridgeProvider.validateBridgeUrl` accepts only http/https schemes; `ModelDownloader.isAcceptableModelUrl` REQUIRES https for any model URL outside loopback + RFC-1918. The Network Security Config permits cleartext globally (Android XML can't express RFC-1918 CIDRs without enumerating ~65 000 entries — see XML comment), so the code-layer checks are the actual enforcement. Bridge JSON carries only anonymised window context per `PCA-S-1`, the least-sensitive payload in the system. | `PCA-S-2` | `HttpBridgeProvider.validateBridgeUrl`, `ModelDownloader.isAcceptableModelUrl`, `res/xml/network_security_config.xml` |
| User pastes a `file://` / `javascript:` / `content://` bridge URL → exfil via OS handler | URL scheme validated before any send; only http / https accepted, blank host rejected | `PCA-S-3` | `HttpBridgeProvider.validateBridgeUrl`, tests `HttpBridgeProviderTest::empty bridge URL fails fast` + dedicated `BridgeUrlValidationTest` |
| Shoulder-surfing the lockscreen reveals advice / "recording in progress" | Both notification channels declared `VISIBILITY_PRIVATE` + per-notification `setVisibility(PRIVATE)` | `PCA-S-4` | `ListeningService.ensureChannels`, `AdviceNotifier.show` |
| MITM-tampered model weights → effectively arbitrary native code | Model downloads require https; cleartext only for loopback / RFC-1918; SHA-256 verification when the spec provides it (the `ModelSpec.sha256` field) | `PCA-S-5` | `ModelDownloader.isAcceptableModelUrl`, `ModelDownloaderTest::sha256 mismatch is detected and the file is rejected`, `ModelDownloaderTest::http to public host is refused` |
| Malicious app on device fires a fake `BOOT_COMPLETED` to coerce service start | `BootReceiver` allowlists the three actions it documents; everything else returns immediately | `PCA-S-6` | `BootReceiver.onReceive` |
| Device theft (post unlock) → adversary reads `transcripts` | Full DB encrypted via SQLCipher; passphrase derived from a Keystore-backed AES/GCM key; StrongBox requested where available | `PCA-S-7` | `DbPassphrase`, `PcaDatabase.build` |
| Device theft (pre unlock) | Out of scope — depends on the user's device-credential strength. We do not weaken Android's default at-rest encryption. | — | OS |
| Owner wipes data → encrypted DB file becomes orphaned and gets opened by a new Keystore key on next launch → crash loop | `wipeEverything` closes the DB, deletes the SQLCipher file, drops model weights, then clears DataStore + the Keystore-wrapped passphrase, in that order | `PCA-S-8` | `SettingsViewModel.wipeEverything` |
| Per-chunk re-recognition by the system `SpeechRecognizer` could grab the mic from the always-on `AudioCapture` | Removed `AndroidSpeechRecognizerImpl`; the only fallback is `NoopSpeechRecognizer` returning empty results | `PCA-S-9` | `AdaptiveSpeechRecognizer`, `NoopSpeechRecognizer` |
| Long-running fused-location call hangs the 5-min ticker | `LocationProvider.current` wrapped in `withTimeoutOrNull(5s)` | `PCA-S-10` | `LocationProvider` |
| Adversarial / partial bridge response crashes the pipeline | `WindowProcessor.process` catches the router decide path and persists the window as queued-but-not-sent | `PCA-S-11` | `WindowProcessor.process` |
| Corrupted DataStore enum value crashes the settings flow | Safe `parseEnum<E>()` with default fallback | `PCA-S-12` | `AppSettings.parseEnum` |
| Path traversal via spec filename | All `ModelSpec.filename` values are compile-time constants under `ModelRegistry`; validated by `ModelRegistryTest::filenames are filesystem-safe` | `PCA-S-13` | `ModelRegistry`, test |
| ReDoS via crafted transcript | Every Anonymizer regex uses bounded `{n,m}` quantifiers; the patterns are documented in `Anonymizer.RULES` | `PCA-S-14` | `Anonymizer` |
| Backup / cloud-restore leaks DB and DataStore | `android:allowBackup=false`; `data_extraction_rules` excludes every domain | `PCA-S-15` | `AndroidManifest.xml`, `data_extraction_rules.xml` |
| Wipe leaves stale ONNX session resident → next embedding uses deleted weights | `OnnxEcapaIdentifier.ensureSession` tracks loaded path + size + mtime, closes the cached session when the file disappears or is replaced | `PCA-S-16` | `OnnxEcapaIdentifier`, `AdaptiveSpeakerResetTest` |
| Wipe / re-download leaves stale whisper.cpp ctx resident | `WhisperJniRecognizer.ensureLoaded` checks path + size + mtime; `recognize` actively releases when file is gone | `PCA-S-17` | `WhisperJniRecognizer`, `AdaptiveSpeechResetTest` |
| Wipe-while-running races: foreground service / dashboard DAO observers crash on closed DB | `SettingsViewModel.wipeEverything` sequences: stop service → release native ctx → wipe tables → close + delete DB file → drop model files → wipe DataStore + Keystore → `Process.killProcess` | `PCA-S-18` | `SettingsViewModel.wipeEverything` |
| Spec §3.3 — `memory_note` returned by the LLM never flowed into the L1 layer | `HourRollupWorker` now decodes each `windows.llmResponseJson`, extracts `memoryNote`, and writes the list into `hour_summaries.memoryNotesJson` via `MemoryRollup.aggregateHour` | `PCA-B-4`  | `MemoryRollup`, `MemoryRollupTest` |

## 4. Cryptography

| Surface | Algorithm | Notes |
|---------|-----------|-------|
| Data-at-rest (Room) | SQLCipher (AES-256-CBC + HMAC-SHA-512) | Passphrase is a 32-byte CSPRNG output |
| Passphrase wrapping | AES-256-GCM in `AndroidKeyStore` | StrongBox requested via `setIsStrongBoxBacked(true)` when `FEATURE_STRONGBOX_KEYSTORE` is present |
| Network (bridge, model downloads) | TLS 1.2+ via OkHttp + the system trust store | `network_security_config` adds the user trust store but does **not** override pins |
| Voice embedding storage | None — opaque float vector, encrypted only by the surrounding SQLCipher | Not a credential; a recovered embedding allows similarity comparison but not voice synthesis |

The passphrase never appears in cleartext on disk and never crosses the
JNI boundary — it lives only in the JVM heap during DB open.

## 5. Privacy boundaries

- Microphone audio **never** crosses the JNI boundary back to Java in a
  form that could be stored — `whisper_jni.cpp` consumes a JNI primitive
  short array, decodes it, and discards the buffer with `JNI_ABORT`.
- The anonymisation token map is a `LinkedHashMap` instantiated per
  `Anonymizer.anonymize` call and lives only inside an
  `AnonymizationResult` value object. It is intentionally NOT persisted —
  see `entity.Entities.kt` (`anonymization_map(…NOT STORED!…)`).
- The LLM payload only sees: anonymised window text, place **label**
  (bucketed cell or geofence name — never raw lat/lng), owner-presence
  boolean, list of open-thread topics. Raw lat/lng is stored only in
  `transcripts` for the owner's own audit view, never sent.
- Logcat: there are no `Log.*` statements that print the transcript, the
  passphrase, the embedding, the bridge URL, or any anonymisation token
  map. `WhisperJniRecognizer` logs the model filename on load (file-system
  name only — no path traversal risk). `ProviderRouter` logs nothing.

## 6. Update / supply chain

- **whisper.cpp**: pinned at v1.7.1 via `CMakeLists.txt`
  `FetchContent_Declare(... GIT_TAG v1.7.1 GIT_SHALLOW TRUE ...)`.
  Bumping requires a code change, a CI run, and a new APK build.
- **ONNX Runtime Android**: pinned at 1.19.2 in `libs.versions.toml`.
- **Model weights**: every `ModelSpec` carries a default URL; users may
  override per-spec. SHA-256 verification runs when the spec specifies one.
  We do **not** currently distribute SHA-256s for the HF defaults because
  they change between revisions of the upstream conversion; adding pinned
  hashes is the right next step and is tracked alongside this document.

## 7. Reporting

This is a personal-use research artefact, not a hosted service. For
security questions or coordinated disclosure, open a private issue on the
repository hosting this module and tag `security`.
