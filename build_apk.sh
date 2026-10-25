#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"

echo "=================================================="
echo "    📦 KovanLLM Android APK Derleniyor           "
echo "=================================================="

cd "${SCRIPT_DIR}/android"
./gradlew assembleDebug

APK_SRC="${SCRIPT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"
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
