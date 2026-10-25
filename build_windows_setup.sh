#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ISCC_PATH="${HOME}/.wine/drive_c/Program Files (x86)/Inno Setup 6/ISCC.exe"

if [ -f "$ISCC_PATH" ]; then
    echo "=================================================="
    echo "    📦 KovanLLM Windows Installer Derleniyor      "
    echo "=================================================="
    wine "$ISCC_PATH" "${SCRIPT_DIR}/installer/kovanllm_setup.iss"
    echo ""
    echo "✅ Windows Kurulum Dosyası Başarıyla Üretildi:"
    ls -lh "${SCRIPT_DIR}/dist/KovanLLM_Station_v1.1_Setup.exe"
else
    echo "❌ Inno Setup (ISCC.exe) bulunamadı."
    exit 1
fi
