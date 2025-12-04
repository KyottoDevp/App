#!/bin/bash

set -e

echo "=========================================="
echo "   HANNSAPP - Android SDK Setup Script"
echo "=========================================="

ANDROID_HOME="$HOME/android-sdk"
CMDLINE_TOOLS_VERSION="11076708"
BUILD_TOOLS_VERSION="34.0.0"
PLATFORM_VERSION="34"

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION:$PATH"

mkdir -p "$ANDROID_HOME"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "[1/5] Downloading Android Command Line Tools..."
    cd /tmp
    curl -sS -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    
    echo "[2/5] Extracting Command Line Tools..."
    unzip -q -o cmdline-tools.zip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
    rm cmdline-tools.zip
else
    echo "[1/5] Command Line Tools already installed, skipping..."
    echo "[2/5] Skipping extraction..."
fi

echo "[3/5] Accepting Android SDK licenses..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

echo "[4/5] Installing SDK components..."
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
    "platform-tools" \
    "platforms;android-$PLATFORM_VERSION" \
    "build-tools;$BUILD_TOOLS_VERSION" \
    --sdk_root="$ANDROID_HOME"

echo "[5/5] Creating local.properties..."
cat > "$(dirname "$0")/local.properties" << EOF
sdk.dir=$ANDROID_HOME
EOF

echo ""
echo "=========================================="
echo "   Android SDK Setup Complete!"
echo "=========================================="
echo "ANDROID_HOME: $ANDROID_HOME"
echo ""
echo "Add these to your environment:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export ANDROID_SDK_ROOT=$ANDROID_HOME"
echo "  export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
echo ""
