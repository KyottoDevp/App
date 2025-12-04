#!/bin/bash

echo "=========================================="
echo "   HANNSAPP - ADB Wi-Fi Connection"
echo "=========================================="
echo ""

ANDROID_HOME="$HOME/android-sdk"
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if ! command -v adb &> /dev/null; then
    echo "ADB not found. Running Android SDK setup..."
    bash setup_android_sdk.sh
fi

show_help() {
    echo "Usage:"
    echo "  ./adb_connect.sh pair <IP:PORT> <PAIRING_CODE>"
    echo "  ./adb_connect.sh connect <IP:PORT>"
    echo "  ./adb_connect.sh devices"
    echo "  ./adb_connect.sh disconnect"
    echo ""
    echo "Examples:"
    echo "  ./adb_connect.sh pair 192.168.1.100:37123 123456"
    echo "  ./adb_connect.sh connect 192.168.1.100:5555"
    echo ""
    echo "To enable Wi-Fi debugging on your Android device:"
    echo "  1. Go to Settings > Developer Options"
    echo "  2. Enable 'Wireless debugging'"
    echo "  3. Tap 'Pair device with pairing code'"
    echo "  4. Use the IP:PORT and code shown on screen"
}

case "$1" in
    pair)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo "Error: Missing arguments"
            echo "Usage: ./adb_connect.sh pair <IP:PORT> <PAIRING_CODE>"
            exit 1
        fi
        echo "Pairing with device at $2..."
        adb pair "$2" "$3"
        ;;
    connect)
        if [ -z "$2" ]; then
            echo "Error: Missing IP:PORT"
            echo "Usage: ./adb_connect.sh connect <IP:PORT>"
            exit 1
        fi
        echo "Connecting to device at $2..."
        adb connect "$2"
        ;;
    devices)
        echo "Connected devices:"
        adb devices -l
        ;;
    disconnect)
        echo "Disconnecting all devices..."
        adb disconnect
        ;;
    *)
        show_help
        ;;
esac
