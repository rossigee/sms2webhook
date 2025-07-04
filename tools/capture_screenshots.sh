#!/bin/bash

# SMS2Webhook Screenshot Capture Script
# This script runs the UI tests to capture screenshots and copies them to the project directory

set -e

echo "=== SMS2Webhook Screenshot Capture ==="
echo

# Check if JAVA_HOME is set correctly
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    echo "Setting JAVA_HOME to: $JAVA_HOME"
fi

# Clean and build the app
echo "Building the app..."
./gradlew clean assembleDebug assembleAndroidTest

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "ERROR: adb (Android Debug Bridge) not found in PATH!"
    echo
    echo "Please ensure Android SDK is installed and add platform-tools to your PATH:"
    echo "  export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
    echo
    echo "Or if using Android Studio, it's typically located at:"
    echo "  - Linux: ~/Android/Sdk/platform-tools"
    echo "  - macOS: ~/Library/Android/sdk/platform-tools"
    echo "  - Windows: %LOCALAPPDATA%\\Android\\Sdk\\platform-tools"
    echo
    echo "Alternatively, you can run the test directly from Android Studio:"
    echo "  1. Open the project in Android Studio"
    echo "  2. Navigate to app/src/androidTest/java/org/golder/sms2webhook/ScreenshotTest.java"
    echo "  3. Right-click on the test and select 'Run ScreenshotTest'"
    exit 1
fi

# Check if a device is connected
echo
echo "Checking for connected devices..."
adb devices | grep -v "List of devices" | grep "device$" > /dev/null
if [ $? -ne 0 ]; then
    echo "ERROR: No Android device or emulator connected!"
    echo "Please connect a device or start an emulator and try again."
    exit 1
fi

# Get the first connected device
DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo "Using device: $DEVICE"

# Install the app and test APK
echo
echo "Installing app and test APK..."
adb -s $DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Clear app data to ensure clean state
echo
echo "Clearing app data..."
adb -s $DEVICE shell pm clear org.golder.sms2webhook || true

# Run the screenshot test
echo
echo "Running screenshot test..."
adb -s $DEVICE shell am instrument -w -e class org.golder.sms2webhook.ScreenshotTest \
    org.golder.sms2webhook.test/androidx.test.runner.AndroidJUnitRunner

# Wait a moment for files to be written
sleep 2

# Create screenshots directory in project
mkdir -p screenshots

# Pull screenshots from device
echo
echo "Pulling screenshots from device..."
# Try multiple possible locations
SCREENSHOT_PATHS=(
    "/sdcard/Android/data/org.golder.sms2webhook/files/Pictures/screenshots"
    "/storage/emulated/0/Android/data/org.golder.sms2webhook/files/Pictures/screenshots"
    "/sdcard/sms2webhook_screenshots"
    "/storage/emulated/0/sms2webhook_screenshots"
)

FOUND_SCREENSHOTS=false
for SCREENSHOT_PATH in "${SCREENSHOT_PATHS[@]}"; do
    echo "Trying path: $SCREENSHOT_PATH"
    if adb -s $DEVICE shell "[ -d $SCREENSHOT_PATH ] && echo exists" | grep -q exists; then
        echo "Found screenshots at: $SCREENSHOT_PATH"
        adb -s $DEVICE pull $SCREENSHOT_PATH/. screenshots/
        FOUND_SCREENSHOTS=true
        break
    fi
done

if [ "$FOUND_SCREENSHOTS" = false ]; then
    echo "WARNING: Could not find screenshots on device"
    echo "The test may have failed or screenshots may be in a different location"
fi

# List captured screenshots
echo
echo "=== Captured Screenshots ==="
if [ -d screenshots ] && [ "$(ls -A screenshots)" ]; then
    ls -la screenshots/*.png 2>/dev/null || echo "No PNG files found"
    
    # Convert screenshots to smaller size for README if ImageMagick is available
    if command -v convert &> /dev/null; then
        echo
        echo "Creating README-sized versions..."
        mkdir -p screenshots/readme
        for file in screenshots/*.png; do
            if [ -f "$file" ]; then
                filename=$(basename "$file")
                convert "$file" -resize 300x screenshots/readme/"${filename%.png}_thumb.png"
                echo "Created thumbnail: ${filename%.png}_thumb.png"
            fi
        done
    else
        echo
        echo "Note: Install ImageMagick to automatically create README-sized thumbnails"
    fi
else
    echo "No screenshots found in the screenshots directory"
fi

echo
echo "=== Screenshot capture complete! ==="
echo "Screenshots are saved in the 'screenshots' directory"
echo "You can now add them to your README.md"