# Personal Context Assistant (PCA) — Android

Реализация спецификации `Android_Assistant_Spec_EN.pdf` (Personal Context
Assistant). Цель — MVP, который собирается и работает на **Samsung Galaxy S21
Ultra (SM-G998B/DS, Exynos 2100, Android 14)**.

> Это отдельный проект от `android/` (Rescue911 OSINT) — они живут в одном
> репозитории, но никак друг друга не трогают.

## Возможности (MVP)

| Слой по спеке (§2.1)        | Реализация в MVP                                                                   |
|-----------------------------|------------------------------------------------------------------------------------|
| 1. Audio capture            | `ListeningService` (FGS, type=`microphone+location+dataSync`) + `AudioRecord` 16 kHz |
| 2. VAD                      | Энергетический RMS-VAD (Silero VAD ONNX подключается за тем же интерфейсом)         |
| 3. STT                      | Android `SpeechRecognizer` (без скачивания моделей). Whisper.cpp swap-in ready.   |
| 4. Speaker ID               | Голосовой эмбеддинг RMS+ZCR (ECAPA-TDNN ONNX в проде), cosine vs reference         |
| 5. Context aggregator       | `WindowAggregator` собирает L0 окно из транскриптов + локации                       |
| 6. Anonymizer               | Regex NER (email, phone, IBAN, card, URL, geo); токен-мапа живёт только в RAM      |
| 7. Decision LLM             | `ProviderRouter`: primary = HTTP-bridge (codex/Gemini), fallback = MockLocal       |
| 8. Memory hierarchy         | WorkManager L1 (час), L2 (день), L3 (профиль, неделя)                              |
| 9. Notification layer       | Foreground + advice-notifications с кнопками feedback (`useful / no / not_now`)    |

Шифрование БД: **SQLCipher** + 256-битная парольная фраза в **AndroidKeystore**
(StrongBox запрашивается при наличии аппаратного TEE).

Мульти-язык: `en`, `ru`, `iw` (Hebrew RTL).

## Сборка APK

Нужно: JDK 17, Android SDK + NDK, Android Build Tools 34. Каталог Android SDK
указывается через `ANDROID_HOME` или `local.properties`.

```bash
cd pca-android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Release-сборка (требует подписи):

```bash
./gradlew :app:assembleRelease \
  -PPCA_KEYSTORE_PATH=$HOME/keystores/pca.jks \
  -PPCA_KEYSTORE_PASSWORD=*** \
  -PPCA_KEY_ALIAS=pca \
  -PPCA_KEY_PASSWORD=***
```

## Установка на SM-G998B/DS

1. Включите **отладку по USB** в `Настройки → Параметры разработчика`.
2. Подключите кабелем; на устройстве разрешите отладку для нового ключа.
3. Поставьте APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

При первом запуске пройдите экраны: Welcome → Consent → Permissions →
Enrollment (биометрия + 5 фраз) → Finish. После Finish сервис стартует и в
шторке появляется постоянное уведомление «PCA — слушаю».

### Отключить оптимизацию батареи (важно для S21 Ultra OneUI)

```bash
adb shell dumpsys deviceidle whitelist +com.pca.assistant
```

Либо в `Настройки → Приложения → Personal Context Assistant → Батарея →
Без ограничений`.

### Включить голосовой ввод иврита / русского

Speech Services by Google → Языки → скачать `Русский (Россия)` и
`עברית (ישראל)`. Без офлайн-моделей `SpeechRecognizer` будет идти в облако
Google для распознавания.

## LLM-bridge (опционально)

По умолчанию приложение использует встроенный `MockLocalProvider` (полностью
оффлайн, без сети). Чтобы подключить настоящий codex / Gemini CLI, поднимите
референс-мост на любой машине:

```bash
cd bridge
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python server.py --provider codex --port 8765
```

Затем в приложении: `Настройки → LLM provider → HTTP bridge` и URL вида
`http://192.168.x.x:8765`.

## Самый важный безопасность / приватность чек-лист (§7.2)

- [x] Сырое аудио НЕ хранится — только текст после STT.
- [x] БД зашифрована SQLCipher; ключ в AndroidKeystore.
- [x] В LLM уходит только анонимизированный текст; токен-мапа в RAM.
- [x] Кнопка «Полная очистка» в Настройках стирает БД, DataStore и Keystore-ключ.
- [x] Постоянное foreground-уведомление «recording in progress» (требование §3.1 / Android 14).
- [x] В режиме MockLocal сеть НЕ используется вообще.

## Дорожная карта до Whisper / ONNX (§12)

Текущий интерфейс `com.pca.assistant.stt.SpeechRecognizer` принимает 16-kHz
PCM и возвращает `SttResult`. Реализация на whisper.cpp:

1. Собрать `libwhisper.so` для `arm64-v8a` (Exynos 2100 → NEON, без QNN).
2. Положить в `app/src/main/jniLibs/arm64-v8a/`.
3. Реализовать `WhisperJniRecognizer : SpeechRecognizer` (см. doc-комментарий
   в `SpeechRecognizer.kt`).
4. В `BindingsModule` поменять `@Binds AndroidSpeechRecognizerImpl` на новый.
5. Добавить on-demand загрузку модели в `SettingsScreen` (UI-крючки уже есть).

Подробности — см. §12 спецификации (Large-v3 turbo Q5 базовый, Ivrit.AI
бустер для иврита, LoRA RU+HE после сбора датасета).
