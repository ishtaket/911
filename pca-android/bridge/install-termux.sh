#!/data/data/com.termux/files/usr/bin/bash
#
# install-termux.sh — поднимает PCA bridge целиком на телефоне через Termux.
#
# Запуск:
#   pkg install -y curl
#   curl -fsSL https://raw.githubusercontent.com/ishtaket/911/claude/build-samsung-app-YaU0S/pca-android/bridge/install-termux.sh | bash
#
# Или, если уже склонировал репозиторий:
#   cd pca-android/bridge && bash install-termux.sh
#
# Что делает:
#   1. Ставит Termux-пакеты (python, nodejs, git, etc.)
#   2. Создаёт venv с FastAPI + Uvicorn
#   3. Ставит codex CLI и Gemini CLI через npm
#   4. Просит ключи API (или скипает если уже есть в окружении)
#   5. Создаёт launcher-скрипт ~/run-pca-bridge.sh

set -euo pipefail

echo "== PCA bridge installer for Termux =="
echo

# --- 1. Termux packages ----------------------------------------------------
echo "[1/5] Installing Termux packages..."
pkg update -y
pkg install -y python python-pip git nodejs-lts curl termux-api

# --- 2. Python venv + bridge requirements ---------------------------------
PCA_HOME="${HOME}/pca-bridge"
mkdir -p "${PCA_HOME}"

if [[ ! -f "${PCA_HOME}/server.py" ]]; then
    echo "[2/5] Fetching server.py..."
    curl -fsSL -o "${PCA_HOME}/server.py" \
        "https://raw.githubusercontent.com/ishtaket/911/claude/build-samsung-app-YaU0S/pca-android/bridge/server.py"
fi

if [[ ! -d "${PCA_HOME}/.venv" ]]; then
    echo "[2/5] Creating venv..."
    python -m venv "${PCA_HOME}/.venv"
fi

# shellcheck disable=SC1091
source "${PCA_HOME}/.venv/bin/activate"
pip install --quiet --upgrade pip
pip install --quiet fastapi 'uvicorn[standard]' pydantic

# --- 3. CLI tools via npm --------------------------------------------------
echo "[3/5] Installing codex + gemini CLIs (npm)..."
# Anthropic Claude Code CLI (`codex` historical name; the modern package is
# @anthropic-ai/claude-code which still exposes a `codex`-compatible entry
# via the `cc` alias). The bridge calls whichever binary you pick; edit
# server.py if your CLI is named differently.
npm install -g @anthropic-ai/claude-code 2>/dev/null || \
    echo "  ! claude-code install failed — try manually: npm install -g @anthropic-ai/claude-code"

# Google Gemini CLI
npm install -g @google/gemini-cli 2>/dev/null || \
    echo "  ! gemini-cli install failed — try manually: npm install -g @google/gemini-cli"

# --- 4. API keys -----------------------------------------------------------
echo
echo "[4/5] API keys"
ENV_FILE="${PCA_HOME}/.env"
touch "${ENV_FILE}"
chmod 600 "${ENV_FILE}"

if ! grep -q '^ANTHROPIC_API_KEY=' "${ENV_FILE}" 2>/dev/null; then
    read -r -p "Anthropic API key (для codex / Claude; Enter чтобы пропустить): " ANTHROPIC_KEY || true
    if [[ -n "${ANTHROPIC_KEY:-}" ]]; then
        echo "ANTHROPIC_API_KEY=${ANTHROPIC_KEY}" >> "${ENV_FILE}"
    fi
fi

if ! grep -q '^GEMINI_API_KEY=' "${ENV_FILE}" 2>/dev/null; then
    read -r -p "Google Gemini API key (для fallback; Enter чтобы пропустить): " GEMINI_KEY || true
    if [[ -n "${GEMINI_KEY:-}" ]]; then
        echo "GEMINI_API_KEY=${GEMINI_KEY}" >> "${ENV_FILE}"
    fi
fi

# --- 5. Launcher ----------------------------------------------------------
echo "[5/5] Writing launcher to ~/run-pca-bridge.sh"
cat > "${HOME}/run-pca-bridge.sh" <<'LAUNCHER'
#!/data/data/com.termux/files/usr/bin/bash
# Запуск PCA bridge.  Loopback only — никаких внешних коннектов на телефон.
set -e
PCA_HOME="${HOME}/pca-bridge"
cd "${PCA_HOME}"
# shellcheck disable=SC1091
source .venv/bin/activate
# Подтягиваем ключи API (если файл есть)
if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
# Termux wake-lock — иначе Android прибьёт процесс при засыпании экрана
termux-wake-lock 2>/dev/null || true
# По умолчанию primary=codex.  Поменяй на --provider gemini если нужен Gemini.
python server.py --host 127.0.0.1 --port 8765 --provider codex
LAUNCHER
chmod +x "${HOME}/run-pca-bridge.sh"

echo
echo "== Готово! =="
echo
echo "Запустить bridge:"
echo "  ~/run-pca-bridge.sh"
echo
echo "В приложении PCA: Настройки → LLM provider → HTTP bridge → URL:"
echo "  http://127.0.0.1:8765"
echo
echo "Чтобы bridge выживал ребуты, добавь запуск в ~/.termux/boot/ (нужен"
echo "пакет termux-boot из F-Droid)."
