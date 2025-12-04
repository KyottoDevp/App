#!/bin/bash

set -e

echo "=========================================="
echo "   HANNSAPP - APK Installation Script"
echo "=========================================="
echo ""

ANDROID_HOME="$HOME/android-sdk"
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"

APK_PATH="${1:-output/Hannsapp-debug.apk}"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    echo "Run './build_apk.sh' first to build the APK"
    exit 1
fi

echo "Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo ""
    echo "No devices connected!"
    echo ""
    echo "To connect your device via Wi-Fi debugging:"
    echo "  1. Enable Developer Options on your Android device"
    echo "  2. Enable Wireless debugging"
    echo "  3. Tap 'Pair device with pairing code'"
    echo "  4. Run: adb pair <IP>:<PAIRING_PORT> <PAIRING_CODE>"
    echo "  5. Run: adb connect <IP>:<PORT>"
    echo ""
    echo "Or connect via USB and enable USB debugging"
    exit 1
fi

echo "Found $DEVICES device(s)"
echo ""

echo "Installing APK..."
adb install -r "$APK_PATH"

echo ""
echo "=========================================="
echo "   Installation Complete!"
echo "=========================================="
echo ""
echo "Launching Hannsapp..."
adb shell am start -n "com.hannsapp.fpscounter/.ui.MainActivity"
