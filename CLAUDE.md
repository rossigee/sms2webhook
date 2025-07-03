# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS2Webhook is an Android application that monitors incoming SMS messages and forwards them to a configured webhook endpoint. The app maintains a local cache using Room database to track processed messages and prevent duplicates.

## Build and Development Commands

```bash
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

- **MainActivity**: Main screen with activity log, statistics, and sync controls
- **SettingsActivity/Fragment**: Configuration for webhook URL and API key
- Uses deprecated UI patterns (should be modernized with MVVM)

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
- Permissions handling: MainActivity.java:123-169
- SMS reading logic: SmsStoreWorkerRunnable.java:88-159
- Webhook upload: WebhookUploader.java:17-98
- Database schema: CacheDatabase.java:15-25

## Development Guidelines

### Current Issues to Address
1. **UI Modernization**: Replace hard-coded dimensions with ConstraintLayout constraints
2. **Architecture**: Implement MVVM pattern with ViewModels and LiveData
3. **Material Design**: Add proper theming (currently empty theme files)
4. **Performance**: Move database operations off main thread
5. **Error Handling**: Improve user feedback for failures

### When Making Changes
- Maintain compatibility with Android SDK 34+ (minSdk)
- Preserve SMS permission handling flow (critical for app function)
- Keep webhook format consistent unless coordinating backend changes
- Test with both single and dual SIM devices
- Ensure background processing works with Android's battery optimizations

### Testing Considerations
- No existing tests - consider adding when implementing new features
- Test permission denial scenarios
- Verify behavior with large SMS volumes
- Test webhook failures and retry logic
- Validate duplicate detection works correctly