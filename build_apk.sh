#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"

MODE="${1:-release}"

echo "=================================================="
echo "    📦 KovanLLM Android APK Derleniyor ($MODE)   "
echo "=================================================="

cd "${SCRIPT_DIR}/android"

if [ "$MODE" = "debug" ]; then
    ./gradlew assembleDebug
    APK_SRC="${SCRIPT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"
else
    ./gradlew assembleRelease
    APK_SRC="${SCRIPT_DIR}/android/app/build/outputs/apk/release/app-release.apk"
fi

APK_DEST="${SCRIPT_DIR}/KovanLLM.apk"

if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" "$APK_DEST"
    echo ""
    echo "✅ APK Başarıyla Üretildi: ${APK_DEST}"
    ls -lh "$APK_DEST"
else
    echo "❌ APK dosyası bulunamadı."
    exit 1
fi
