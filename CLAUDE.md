# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS2Webhook is an Android application that monitors incoming SMS messages and forwards them to a configured webhook endpoint. The app maintains a local cache using Room database to track processed messages and prevent duplicates.

## Version 2.0.0 Updates (Current)

### Major Improvements
- **MVVM Architecture**: Complete refactor to use ViewModels and LiveData
- **Material Design 3**: Full MD3 theming with proper color schemes
- **Modern UI**: RecyclerView for logs, SwipeRefreshLayout, proper ConstraintLayout
- **Error Handling**: Comprehensive try-catch blocks to prevent crashes
- **Threading**: All database operations moved off main thread
- **Compatibility**: Lowered minSdk from 34 to 28 for broader device support

## Build and Development Commands

```bash
# Set Java 17 for building
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Build the app
./gradlew build

# Install debug APK on connected device
./gradlew installDebug

# Run all checks (lint, etc.)
./gradlew check

# Clean build artifacts
./gradlew clean

# Generate signed APK
./gradlew assembleRelease

# Debug installation script (includes logcat monitoring)
./debug_install.sh
```

## Architecture Overview

### Core Components

1. **SmsBroadcastReceiver** (app/src/main/java/org/golder/sms2webhook/SmsBroadcastReceiver.java)
   - Receives SMS_RECEIVED broadcasts
   - Extracts SMS data and queues for processing
   - Entry point for new messages

2. **SmsStoreWorker** (app/src/main/java/org/golder/sms2webhook/SmsStoreWorker.java)
   - WorkManager worker for background processing
   - Manages the upload queue and retry logic
   - Handles both new SMS and historical sync

3. **WebhookUploader** (app/src/main/java/org/golder/sms2webhook/WebhookUploader.java)
   - Handles HTTP communication with webhook
   - Implements retry logic and error handling
   - Sends SMS data as JSON with optional API key authentication

4. **Room Database** (CacheDatabase, CacheDao, CacheEntry)
   - Tracks processed message hashes to prevent duplicates
   - Uses SHA-256 hashing for message identification
   - Provides persistence across app restarts

### UI Structure

- **MainActivity**: Main screen with activity log, statistics, and sync controls (MVVM pattern)
- **MainViewModel**: Handles UI state and business logic for MainActivity
- **SettingsActivity/Fragment**: Configuration for webhook URL, API key, and test connection
- **LogAdapter**: RecyclerView adapter for displaying color-coded log entries

## Key Implementation Details

### SMS Processing Flow
1. SMS received → SmsBroadcastReceiver
2. Message queued → SmsStoreWorker
3. Hash checked against cache → DigestCache
4. If new, upload to webhook → WebhookUploader
5. On success, add to cache → CacheDatabase

### Webhook Format
```json
{
  "from": "+1234567890",
  "to": "+0987654321",
  "text": "Message content",
  "timestamp": 1234567890000,
  "sim": 0,
  "apiKey": "optional-api-key"
}
```

### Critical Files
- Permissions handling: MainActivity.java:44-94
- SMS reading logic: SmsStoreWorkerRunnable.java:88-159
- Webhook upload: WebhookUploader.java:17-98
- Database schema: CacheDatabase.java:15-25
- ViewModel implementation: MainViewModel.java
- Error handling: MainApplication.java:26-61

## Development Guidelines

### Recently Resolved Issues (v2.0.0)
1. ✅ **UI Modernization**: Implemented proper ConstraintLayout with RecyclerView
2. ✅ **Architecture**: Full MVVM pattern with ViewModels and LiveData
3. ✅ **Material Design**: Complete MD3 theming with color schemes
4. ✅ **Performance**: All database operations moved off main thread
5. ✅ **Error Handling**: Comprehensive try-catch blocks and user feedback
6. ✅ **Pixel 8 Crash**: Fixed by lowering minSdk to 28 and adding error handling

### When Making Changes
- Maintain compatibility with Android SDK 28+ (minSdk lowered for device compatibility)
- Preserve SMS permission handling flow (critical for app function)
- Keep webhook format consistent unless coordinating backend changes
- Test with both single and dual SIM devices
- Ensure background processing works with Android's battery optimizations
- Always wrap initialization code in try-catch blocks to prevent crashes
- Use Java 17 for building (set JAVA_HOME if needed)

### Testing Considerations
- No existing tests - consider adding when implementing new features
- Test permission denial scenarios
- Verify behavior with large SMS volumes
- Test webhook failures and retry logic
- Validate duplicate detection works correctly
- Use debug_install.sh script to capture crash logs from devices
- Test on physical devices (especially Pixel phones) in addition to emulators

### Known Device Compatibility
- ✅ Android emulators (API 28+)
- ✅ Pixel 8 (after minSdk and error handling fixes)
- ⚠️ Requires SMS permissions to be granted manually on first run

## CI/CD and Release Process

### GitHub Actions Workflows

1. **release.yml** - Main release workflow triggered on version tags
   - Builds both debug and release APKs
   - Signs release APK using GitHub secrets
   - Creates GitHub release with changelog
   - Uploads both APKs as release artifacts

2. **sign-apk-manual.yml** - Alternative manual signing workflow
   - Handles keystore format conversion automatically
   - Uses apksigner directly instead of GitHub Action
   - More robust against keystore format issues

3. **test-keystore.yml** - Diagnostic workflow for keystore issues
   - Tests keystore format and compatibility
   - Attempts automatic conversion to PKCS12
   - Outputs fixed keystore in base64 if successful

### APK Signing Setup

**IMPORTANT**: Never commit keystore files to the repository!

1. Generate a keystore (if needed):
```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias release-key \
  -storetype PKCS12
```

2. Convert keystore to base64:
```bash
base64 release-keystore.jks
```

3. Add GitHub Secrets (Settings → Secrets and variables → Actions):
   - `SIGNING_KEY`: Base64-encoded keystore file
   - `ALIAS`: Key alias (e.g., "release-key")
   - `KEY_STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password (often same as keystore password)

### Keystore Troubleshooting

If you encounter "Tag number over 30 is not supported" errors:
1. Run the test-keystore.yml workflow to diagnose
2. Consider regenerating the keystore in PKCS12 format
3. Use the manual signing workflow as a fallback

The `.gitignore` file is configured to exclude all keystore files:
- `*.jks`
- `*.keystore`
- `*.p12`
- `release-keystore.*`