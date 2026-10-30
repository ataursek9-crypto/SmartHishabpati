#!/usr/bin/env bash
set -e

echo "=================================================="
echo "🚀 Smart Business Manager - APK & Project Export"
echo "=================================================="

# 1. Build and export via Gradle task
echo "📦 Building Android Debug APK and packaging SmartBusinessManager_Project.zip..."
gradle exportProjectZip

APK_PATH="APK/SmartBusinessManager.apk"
ZIP_PATH="SmartBusinessManager_Project.zip"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: APK not found at $APK_PATH"
    exit 1
fi

if [ ! -f "$ZIP_PATH" ]; then
    echo "❌ Error: ZIP not found at $ZIP_PATH"
    exit 1
fi

APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null)
APK_SIZE_MB=$(( APK_SIZE / 1048576 ))

ZIP_SIZE=$(stat -c%s "$ZIP_PATH" 2>/dev/null || stat -f%z "$ZIP_PATH" 2>/dev/null)
ZIP_SIZE_MB=$(( ZIP_SIZE / 1048576 ))

echo "🔍 Verifying ZIP contents and integrity..."
if ! unzip -t "$ZIP_PATH" > /dev/null 2>&1; then
    echo "❌ Error: Corrupted ZIP file!"
    exit 1
fi

if ! unzip -l "$ZIP_PATH" | grep -q "APK/SmartBusinessManager.apk"; then
    echo "❌ Error: APK/SmartBusinessManager.apk missing inside ZIP!"
    exit 1
fi

if ! unzip -l "$ZIP_PATH" | grep -q "README_BN.txt"; then
    echo "❌ Error: README_BN.txt missing inside ZIP!"
    exit 1
fi

echo "=================================================="
echo "🎉 Export Complete & Verified Successfully!"
echo "📁 APK Path: $APK_PATH ($APK_SIZE_MB MB)"
echo "📁 Root APK: SmartBusinessManager.apk ($APK_SIZE_MB MB)"
echo "📦 ZIP Path: $ZIP_PATH ($ZIP_SIZE_MB MB)"
echo "✨ Verified: APK/SmartBusinessManager.apk is inside the ZIP!"
echo "✨ Verified: README_BN.txt is inside the ZIP!"
echo "=================================================="
