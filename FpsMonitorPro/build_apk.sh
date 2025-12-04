#!/bin/bash

set -e

echo "=========================================="
echo "   HANNSAPP - APK Build Script"
echo "=========================================="
echo ""

ANDROID_HOME="$HOME/android-sdk"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "Android SDK not found. Running setup first..."
    bash setup_android_sdk.sh
fi

BUILD_TYPE="${1:-debug}"

echo "Build Type: $BUILD_TYPE"
echo ""

if [ ! -f "gradlew" ]; then
    echo "[1/4] Downloading Gradle Wrapper..."
    gradle wrapper --gradle-version 8.4 2>/dev/null || {
        echo "Creating Gradle Wrapper manually..."
        mkdir -p gradle/wrapper
        curl -sS -L -o gradle/wrapper/gradle-wrapper.jar "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
        cat > gradlew << 'GRADLEW'
#!/bin/sh
DIRNAME=$(dirname "$0")
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW
        chmod +x gradlew
    }
else
    echo "[1/4] Gradle Wrapper already exists..."
fi

chmod +x gradlew

echo "[2/4] Cleaning previous build..."
./gradlew clean --no-daemon --warning-mode all 2>&1 | tail -5 || true

echo "[3/4] Building APK ($BUILD_TYPE)..."
if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew assembleRelease --no-daemon --stacktrace
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    ./gradlew assembleDebug --no-daemon --stacktrace
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "[4/4] Build complete!"
echo ""

if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo "=========================================="
    echo "   BUILD SUCCESSFUL!"
    echo "=========================================="
    echo ""
    echo "APK Location: $APK_PATH"
    echo "APK Size: $APK_SIZE"
    echo ""
    
    mkdir -p output
    cp "$APK_PATH" "output/Hannsapp-$BUILD_TYPE.apk"
    echo "Copied to: output/Hannsapp-$BUILD_TYPE.apk"
    echo ""
else
    echo "=========================================="
    echo "   BUILD FAILED!"
    echo "=========================================="
    echo "APK not found at: $APK_PATH"
    exit 1
fi
