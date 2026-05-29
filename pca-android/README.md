# Personal Context Assistant (PCA) — Android

Реализация спецификации `Android_Assistant_Spec_EN.pdf` (Personal Context
Assistant). Цель — MVP, который собирается и работает на **Samsung Galaxy S21
Ultra (SM-G998B/DS, Exynos 2100, Android 14)**.

> Это отдельный проект от `android/` (Rescue911 OSINT) — они живут в одном
> репозитории, но никак друг друга не трогают.

## Возможности (MVP)

| Слой по спеке (§2.1)        | Реализация                                                                          |
|-----------------------------|------------------------------------------------------------------------------------|
| 1. Audio capture            | `ListeningService` (FGS, type=`microphone+location+dataSync`) + `AudioRecord` 16 kHz |
| 2. VAD                      | Энергетический RMS-VAD (Silero VAD ONNX подключается за тем же интерфейсом)         |
| 3. STT                      | **whisper.cpp v1.7.1** через JNI (`libpca_whisper_jni.so`). Android STT — только fallback. |
| 4. Speaker ID               | **ECAPA-TDNN ONNX** через ONNX Runtime Android. Synthetic — только fallback.        |
| 5. Context aggregator       | `WindowAggregator` собирает L0 окно из транскриптов + локации                       |
| 6. Anonymizer               | Regex NER (email, phone, IBAN, card, URL, geo); токен-мапа живёт только в RAM      |
| 7. Decision LLM             | `ProviderRouter`: primary = HTTP-bridge (codex/Gemini), fallback = MockLocal       |
| 8. Memory hierarchy         | WorkManager L1 (час), L2 (день), L3 (профиль, неделя)                              |
| 9. Notification layer       | Foreground + advice-notifications с кнопками feedback (`useful / no / not_now`)    |

### Нативные компоненты

- **whisper.cpp** собирается из исходников через `externalNativeBuild { cmake }`. CMake
  `FetchContent_Declare` тянет `ggerganov/whisper.cpp@v1.7.1` при первой настройке —
  нужен `git` + сеть на build-машине. Кэширование Gradle переиспользуется.
- **ONNX Runtime Android** (`com.microsoft.onnxruntime:onnxruntime-android:1.19.2`)
  подключён как AAR (~15 МБ). Используется для ECAPA-TDNN.
- **NDK r26** (`ndk;26.1.10909125`) + CMake 3.22.1 — указано в `app/build.gradle.kts`
  и в `.github/workflows/pca-ci.yml`.

### Модели (скачиваются по требованию)

APK остаётся <30 МБ. Веса берутся в первый запуск через UI Настройки → «Загрузка моделей»:

| Модель                          | Размер     | URL по умолчанию                                  |
|---------------------------------|-----------:|---------------------------------------------------|
| `ggml-large-v3-turbo-q5_0.bin`  | ~800 МБ    | HF `ggerganov/whisper.cpp`                        |
| `ggml-small-q5_0.bin`           | ~466 МБ    | HF `ggerganov/whisper.cpp`                        |
| `ggml-ivrit-turbo-q5_0.bin`     | ~1024 МБ   | HF (требует override URL — нет официальной GGML)  |
| `ecapa-tdnn.onnx`               | ~27 МБ     | HF mirror                                         |

`AdaptiveSpeechRecognizer` и `AdaptiveSpeakerIdentifier` сами переключаются:
пока модели нет на диске — STT возвращает пустые транскрипты (`NoopSpeechRecognizer` —
честный no-op, потому что Android STT конфликтует с always-on `AudioCapture` за
микрофон), а embedding строится синтетически. Как только файл появляется —
следующий чанк уже идёт в Whisper / ONNX без перезапуска.

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

## LLM-bridge

**Это основной режим работы** (per spec §3.4: primary = codex CLI, fallback =
Gemini CLI). Приложение шипуется с дефолтом `provider = BRIDGE`. Пока URL
bridge не задан в Settings, каждое окно автоматически фолбэчится на
`MockLocalProvider` (deep fallback per §3.4 health-check) — никаких подсказок
от настоящего LLM не будет.

### Биллинг-модель: подписка, не API

**Bridge использует CLI через подписку пользователя**, как написано в
спецификации §6.4 («codex CLI», «Gemini CLI»). Это:

- **Codex CLI** (`@openai/codex`) — авторизуется через **ChatGPT Plus / Pro**
  командой `codex login`. Запросы идут в счёт ежемесячного лимита подписки.
- **Claude Code** (`@anthropic-ai/claude-code`) — через **Claude Pro / Max**
  командой `/login` внутри REPL. Запросы в счёт подписки.
- **Gemini CLI** (`@google/gemini-cli`) — через **Google AI / AI Studio**
  подписку, OAuth-flow при первом запуске.

**Никаких API-ключей pay-per-token в проекте нет.** Bridge просто шеллит
CLI как subprocess; учётка живёт в `~/.codex/` / `~/.claude/` / `~/.gemini/`
в файлах самих CLI после успешного логина.

Спецификация §6.4 явно перечисляет три варианта развёртывания bridge.
Выбирай любой:

### Вариант 1 — на ноутбуке / домашнем сервере (рекомендую если есть)

Самый стабильный путь — bridge запущен на машине с зарядом и нормальным CPU,
телефон ходит к нему по Wi-Fi:

```bash
# Поставить CLI и залогиниться по подписке (один раз)
npm install -g @openai/codex
codex login   # откроется браузер для ChatGPT Plus/Pro OAuth

# Поставить bridge
cd bridge
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# --host 127.0.0.1 по умолчанию (PCA-S-23). Для LAN-доступа нужно явно:
python server.py --provider codex --port 8765 --host 192.168.1.50
```

**Security note**: `--host 0.0.0.0` без auth-обёртки — любой в той же
Wi-Fi сможет жечь твою подписку и читать транскрипты. Лучше:
- bind на конкретный LAN-интерфейс (`--host 192.168.x.x`)
- или поставить SSH-туннель `ssh -L 8765:127.0.0.1:8765 user@laptop` и
  в Android'е указать `http://127.0.0.1:8765`
- или поднять nginx с Basic Auth перед bridge

В приложении: `Настройки → LLM provider → HTTP bridge`, URL вида
`http://192.168.x.x:8765` (IP машины в твоей домашней сети).

### Вариант 2 — Termux на самом телефоне (целиком на устройстве)

Если не хочешь зависеть от внешней машины. Bridge крутится локально,
приложение ходит на `127.0.0.1`. **Никакого LAN-трафика вообще** —
вся обработка на устройстве.

1. Поставь **Termux из F-Droid** (не из Google Play — там устаревшая
   версия): https://f-droid.org/packages/com.termux/
2. (опционально) Поставь **Termux:Boot** оттуда же — позволит bridge
   стартовать после ребута.
3. Открой Termux и одной командой развернёшь всё:

   ```bash
   pkg install -y curl
   curl -fsSL https://raw.githubusercontent.com/ishtaket/911/claude/build-samsung-app-YaU0S/pca-android/bridge/install-termux.sh | bash
   ```

   Скрипт:
   - поставит `python`, `nodejs-lts`, `git`, `termux-api`
   - создаст venv с FastAPI + Uvicorn в `~/pca-bridge/`
   - `npm install -g @openai/codex @anthropic-ai/claude-code @google/gemini-cli` —
     три CLI на выбор; bridge говорит с тем, что укажешь в `--provider`
   - создаст launcher `~/run-pca-bridge.sh`
   - **никаких API-ключей не запросит** — учётка лежит у самих CLI

4. **Залогинься в нужный CLI по подписке** (один раз):

   ```bash
   codex login              # ChatGPT Plus/Pro
   # или
   claude                   # Claude Pro/Max — внутри REPL пиши: /login
   # или
   gemini                   # Google AI/AI Studio — first-run OAuth
   ```

   Каждый откроет браузер с OAuth flow; учётка сохранится в `~/.codex/` /
   `~/.claude/` / `~/.gemini/` соответственно. **Запросы идут в счёт
   ежемесячной подписки, не оплачиваются токенами через API.**

5. Запусти bridge:

   ```bash
   ~/run-pca-bridge.sh
   ```

   Loopback only: `--host 127.0.0.1 --port 8765`. Никто из внешней сети
   с твоим bridge поговорить не сможет. Команда вызывает
   `termux-wake-lock` чтобы Android не убил процесс при засыпании экрана.

6. В приложении PCA: `Настройки → LLM provider → HTTP bridge` → URL:

   ```
   http://127.0.0.1:8765
   ```

   Жёлтое предупреждение «Bridge URL не задан» исчезнет; следующее
   5-мин окно пойдёт в codex/Gemini через твою подписку.

#### Чтобы выживало ребуты

- Установи **Termux:Boot** из F-Droid (та же страница `com.termux`).
- Создай `~/.termux/boot/start-pca`:
  ```bash
  mkdir -p ~/.termux/boot
  cat > ~/.termux/boot/start-pca <<'EOF'
  #!/data/data/com.termux/files/usr/bin/sh
  ~/run-pca-bridge.sh > ~/pca-bridge.log 2>&1 &
  EOF
  chmod +x ~/.termux/boot/start-pca
  ```
- После ребута Termux:Boot его дёрнет в фоне.

#### Что Termux ест

На S21 Ultra (Exynos 2100):
- Node + Python в памяти: ~150-200 МБ RSS
- При LLM-запросе (раз в 5 мин) — пиковый CPU на 1-3 сек, затем idle
- Сетевой трафик — только от bridge'а к API провайдеру (Anthropic /
  Google), сам bridge ↔ приложение не идёт через интернет (loopback)
- Батарея: в основном простой; типичная нагрузка на S21 Ultra
  должна добавить 3-5% за 8 часов поверх самого PCA

#### Если нет ни одной подписки

`MockLocalProvider` в самом приложении остаётся доступен — в Settings
выбери chip «Local mock». Никакого Termux не нужно, ни одной копейки за
LLM. Подсказки будут детерминированно-простыми (детектит явные триггеры
и обещания по regex, см. §3.4 «Offline mode»). Это сознательный
spec-документированный fallback, а не bug.

### Вариант 3 — собственный сервер-обёртка (для продакшна)

Если у тебя своя инфраструктура и не хочешь зависеть от npm-пакетов
ни Anthropic ни Google — реализуй `POST /decide` с тем же JSON-контрактом
который описан в `bridge/server.py` (схема `LlmRequest` → `LlmDecision`,
один-в-один со спецификацией §5). Какой именно LLM за этим эндпоинтом —
приложению всё равно.

## Самый важный безопасность / приватность чек-лист (§7.2)

- [x] Сырое аудио НЕ хранится — только текст после STT.
- [x] БД зашифрована SQLCipher; ключ в AndroidKeystore.
- [x] В LLM уходит только анонимизированный текст; токен-мапа в RAM.
- [x] Кнопка «Полная очистка» в Настройках стирает БД, DataStore и Keystore-ключ.
- [x] Постоянное foreground-уведомление «recording in progress» (требование §3.1 / Android 14).
- [x] При выборе режима «Local mock» в Settings — сеть НЕ используется вообще.

## Дорожная карта до Whisper / ONNX (§12)

Whisper.cpp уже встроен — собирается CMake-ом из `app/src/main/cpp/CMakeLists.txt`,
JNI-обёртка `WhisperJniRecognizer` подключена в `BindingsModule` через
`AdaptiveSpeechRecognizer`. Что остаётся пользователю на устройстве:

1. Скачать модель `ggml-large-v3-turbo-q5_0.bin` (~800 МБ) через
   Settings → Model downloads.
2. После скачивания следующий 5-минутный тик автоматически идёт через
   whisper.cpp — без перезапуска приложения.

Дальнейшие улучшения — см. §12 спецификации (Ivrit.AI бустер для иврита,
LoRA RU+HE после сбора датасета).
