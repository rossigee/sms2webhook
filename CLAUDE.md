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
# Set Java 21 for building (required by sourceCompatibility/targetCompatibility)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Build the app
./gradlew build

# Install debug APK on connected device
./gradlew installDebug

# Run all checks (lint, etc.)
./gradlew check

# Clean build artifacts
./gradlew clean

# Generate release APK (debug-key signed unless release signing is configured)
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
- Use Java 21 for building (set JAVA_HOME if needed)

### Gradle Module Layout

The application module directory is `sms2webhook/`, not `app/`. Paths in build scripts,
CI workflows and documentation must use `sms2webhook/build/...`. APK outputs are
`sms2webhook/build/outputs/apk/{debug,release}/sms2webhook-{debug,release}.apk`.

### Zapstore Publishing

`release.yml` publishes the signed release APK to the
[Zapstore](https://zapstore.dev) Nostr relay as its final step, driven by the
`ZAPSTORE_SIGN_WITH` repository secret. The step is a no-op when that secret is
unset, so releases are unaffected until it is configured.

`zapstore.yaml` at the repo root is **required to be committed**. On the first
publish the relay fetches it, verifies the `pubkey` field matches the signing
key, and whitelists the publisher. An unpublished or mismatched `pubkey` means
the release event is rejected.

`ZAPSTORE_SIGN_WITH` accepts either an `nsec1...` or a NIP-46 `bunker://` URL.
Prefer a bunker: it keeps the key off the runner, is revocable, and can be
scoped to `sign_event` for this package. An `nsec` in the runner environment is
readable by any action in the job and cannot be rotated without changing the
publisher identity.

Install path is `github.com/zapstore/zsp/cmd/zsp` — the module root is not a
main package. `zapstore.yaml` pins `match` to `.*-release-signed\.apk$` so the
debug asset attached to the same release is never published to users.

Full setup steps are in `.github/workflows/README.md`.

### Local Gradle Overrides

Never commit `org.gradle.java.home` or signing credentials to `gradle.properties`.
A machine-specific `org.gradle.java.home` breaks CI, which is what caused every
`Android CI` run to fail with "Java home supplied is invalid". Put local overrides in
`~/.gradle/gradle.properties` instead.

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

1. **android.yml** - Continuous integration, triggered on push and pull requests
   - Runs unit tests and lint, publishes error/warning counts in the job summary
   - Publishes debug and release APKs as build artifacts
   - Release APK from this workflow is debug-key signed

2. **release.yml** - Main release workflow triggered on version tags
   - Runs unit tests and builds both debug and release APKs
   - Signs the release APK with the release key during `assembleRelease`
   - Verifies the signature with `apksigner` before publishing
   - Creates the GitHub release with changelog

3. **manual-build.yml** - On-demand builds via workflow dispatch
   - Build type (debug/release/both), optional draft release creation
   - Signs with the release key only when a release is being created

4. **pr-check.yml** - Automated pull request review
   - Lint summary, APK size report, hardcoded secret and plain HTTP scans
   - Posts the results as a pull request comment (needs `pull-requests: write`)

All workflows build with JDK 21 and reference the `sms2webhook/` module directory.

Validate workflow changes with `actionlint -shellcheck= -pyflakes= .github/workflows/*.yml`.

### APK Signing Setup

**IMPORTANT**: Never commit keystore files or signing credentials to the repository!

Signing is performed by Gradle, configured in `sms2webhook/build.gradle` from
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and the optional
`RELEASE_KEY_PASSWORD`. These are passed as environment variables by the workflows
from the GitHub secrets below. See `.github/workflows/README.md` for details.

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
   - `KEY_PASSWORD`: Key password. Optional; defaults to `KEY_STORE_PASSWORD`

The keystore must be PKCS12 format. The "Tag number over 30 is not supported" error
from `keytool` means the keystore is in the legacy JKS format; regenerate it as PKCS12
with the `-storetype PKCS12` flag above.

The `.gitignore` file is configured to exclude all keystore files:
- `*.jks`
- `*.keystore`
- `*.p12`
- `release-keystore.*`