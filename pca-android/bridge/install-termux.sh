#!/data/data/com.termux/files/usr/bin/bash
#
# install-termux.sh — поднимает PCA bridge целиком на телефоне через Termux.
#
# Модель работы (per spec §6.4): codex / Gemini CLI используются через
# подписку пользователя, НЕ через API-ключи pay-per-token. CLI хранят свои
# учётки в ~/.config (своих директориях) после интерактивного `codex login`
# или `gemini auth`. Bridge просто шеллит их как subprocess.
#
# ---------------------------------------------------------------------------
# SECURITY (PCA-S-21): этот скрипт можно запустить двумя способами:
#
#   A) curl | bash  (быстро, доверяй github raw + TLS):
#        pkg install -y curl
#        curl -fsSL <URL> | bash
#
#   B) clone + inspect + run  (рекомендуется параноикам):
#        pkg install -y git
#        git clone https://github.com/ishtaket/911.git
#        cd 911/pca-android/bridge
#        less install-termux.sh    # прочитай что внутри
#        bash install-termux.sh
#
# Вариант B даёт проверить содержимое перед исполнением. Вариант A быстрее
# но опирается на то что нет MitM на пути до github raw (TLS закрывает).
# ---------------------------------------------------------------------------
#
# Что делает:
#   1. Ставит Termux-пакеты (python, nodejs, git, etc.)
#   2. Создаёт venv с FastAPI + Uvicorn
#   3. Ставит codex CLI и Gemini CLI через npm
#   4. Печатает инструкцию по логину (по подписке, без API-ключей)
#   5. Создаёт launcher-скрипт ~/run-pca-bridge.sh

set -euo pipefail

# PCA-S-22: refuse to run anywhere that isn't Termux — somebody piping this
# into a regular Linux shell would otherwise install packages with the
# wrong package manager and write to /data/data/... which doesn't exist.
if [[ ! -d "/data/data/com.termux/files/usr" ]]; then
    echo "ERROR: this script must run inside Termux on Android." >&2
    echo "       /data/data/com.termux/files/usr not found." >&2
    exit 1
fi

echo "== PCA bridge installer for Termux =="
echo

# --- 1. Termux packages ----------------------------------------------------
echo "[1/4] Installing Termux packages..."
pkg update -y
pkg install -y python python-pip git nodejs-lts curl termux-api

# --- 2. Python venv + bridge requirements ---------------------------------
PCA_HOME="${HOME}/pca-bridge"
mkdir -p "${PCA_HOME}"

if [[ ! -f "${PCA_HOME}/server.py" ]]; then
    echo "[2/4] Fetching server.py..."
    curl -fsSL -o "${PCA_HOME}/server.py" \
        "https://raw.githubusercontent.com/ishtaket/911/claude/build-samsung-app-YaU0S/pca-android/bridge/server.py"
fi

if [[ ! -d "${PCA_HOME}/.venv" ]]; then
    echo "[2/4] Creating venv..."
    python -m venv "${PCA_HOME}/.venv"
fi

# shellcheck disable=SC1091
source "${PCA_HOME}/.venv/bin/activate"
pip install --quiet --upgrade pip
pip install --quiet fastapi 'uvicorn[standard]' pydantic

# --- 3. CLI tools via npm --------------------------------------------------
echo "[3/4] Installing codex + Gemini CLIs (npm)..."
echo "        These are the subscription-authenticated CLIs from spec §6.4."

# OpenAI Codex CLI (modern revived version, npm @openai/codex).
# Authenticates against your ChatGPT Plus / Pro subscription via `codex login`.
npm install -g @openai/codex 2>/dev/null || \
    echo "  ! @openai/codex install failed — try manually: npm install -g @openai/codex"

# Anthropic Claude Code CLI (npm @anthropic-ai/claude-code).
# Authenticates against your Claude Pro / Max subscription via `claude /login`.
# Useful as an alternative primary if you have a Claude subscription instead.
npm install -g @anthropic-ai/claude-code 2>/dev/null || \
    echo "  ! @anthropic-ai/claude-code install failed — try manually: npm install -g @anthropic-ai/claude-code"

# Google Gemini CLI (npm @google/gemini-cli).
# Authenticates against your Google AI / AI Studio subscription via `gemini`
# command (first run opens a browser flow).
npm install -g @google/gemini-cli 2>/dev/null || \
    echo "  ! @google/gemini-cli install failed — try manually: npm install -g @google/gemini-cli"

# --- 4. Launcher -----------------------------------------------------------
echo "[4/4] Writing launcher to ~/run-pca-bridge.sh"
cat > "${HOME}/run-pca-bridge.sh" <<'LAUNCHER'
#!/data/data/com.termux/files/usr/bin/bash
# Запуск PCA bridge.  Loopback only — никаких внешних коннектов на телефон.
# CLI должны быть уже залогинены: `codex login` и/или `gemini` интерактивно.
set -e
PCA_HOME="${HOME}/pca-bridge"
cd "${PCA_HOME}"
# shellcheck disable=SC1091
source .venv/bin/activate
# Termux wake-lock — иначе Android прибьёт процесс при засыпании экрана
termux-wake-lock 2>/dev/null || true
# По умолчанию primary=codex.  Поменяй на --provider gemini если нужен Gemini.
python server.py --host 127.0.0.1 --port 8765 --provider codex
LAUNCHER
chmod +x "${HOME}/run-pca-bridge.sh"

echo
echo "===================================================================="
echo "== УСТАНОВКА ЗАВЕРШЕНА.  ОСТАЛОСЬ ЗАЛОГИНИТЬСЯ ПО ПОДПИСКЕ.       =="
echo "===================================================================="
echo
echo "PCA bridge использует CLI по подписке (per spec §6.4), а не через"
echo "API-ключи pay-per-token. Залогинься в тот CLI, чьей подпиской хочешь"
echo "пользоваться:"
echo
echo "  Codex CLI (ChatGPT Plus / Pro):"
echo "      codex login"
echo
echo "  Claude Code (Claude Pro / Max):"
echo "      claude"
echo "      # внутри REPL:  /login"
echo
echo "  Gemini CLI (Google AI / AI Studio):"
echo "      gemini"
echo "      # при первом запуске откроется браузерный flow"
echo
echo "Каждый из них откроет вкладку в браузере для OAuth — учётка"
echo "сохранится в ~/.codex/, ~/.claude/, ~/.gemini/ соответственно."
echo "PCA bridge просто шеллит CLI как subprocess; никакой API key не"
echo "хранится в Termux."
echo
echo "После логина запусти bridge:"
echo "  ~/run-pca-bridge.sh"
echo
echo "В приложении PCA: Настройки → LLM provider → HTTP bridge → URL:"
echo "  http://127.0.0.1:8765"
echo
echo "Чтобы bridge выживал ребуты, добавь запуск в ~/.termux/boot/ (нужен"
echo "пакет termux-boot из F-Droid)."
