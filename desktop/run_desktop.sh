#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================="
echo "    🚀 KovanLLM Masaüstü İstasyonu Başlatılıyor   "
echo "=================================================="

VENV_PYTHON="${ROOT_DIR}/server/.venv/bin/python"

if [ ! -f "$VENV_PYTHON" ]; then
    echo "Python sanal ortamı hazırlanıyor..."
    /home/perfo/.local/bin/uv venv "${ROOT_DIR}/server/.venv"
    /home/perfo/.local/bin/uv pip install --python "${ROOT_DIR}/server/.venv" -r "${ROOT_DIR}/server/requirements.txt" PyQt6 qrcode pillow
fi

cd "${SCRIPT_DIR}"
exec "$VENV_PYTHON" desktop_gui.py
