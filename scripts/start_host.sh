#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================="
echo "      🚀 KovanLLM İstasyonu Başlatılıyor         "
echo "=================================================="

exec "${ROOT_DIR}/desktop/run_desktop.sh"
