# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS2Webhook is an Android application that monitors incoming SMS messages and forwards them to a configured webhook endpoint. The app maintains a local cache using Room database to track processed messages and prevent duplicates.

## The 2.0.0 Rewrite

Everything below still describes the current code, but it is the 2.0.0 rewrite, not
recent news. Per-release detail belongs in `CHANGELOG.md`, which `zapstore.yaml`
reads for published release notes — keep it current when cutting a release.

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

# Watch a device for crashes during a manual test
adb logcat -s AndroidRuntime:E org.golder.sms2webhook:V
```

## Architecture Overview

### Core Components

1. **SmsBroadcastReceiver** (sms2webhook/src/main/java/org/golder/sms2webhook/SmsBroadcastReceiver.java)
   - Receives SMS_RECEIVED broadcasts
   - Extracts SMS data and queues for processing
   - Entry point for new messages

2. **SmsStoreWorker** (sms2webhook/src/main/java/org/golder/sms2webhook/SmsStoreWorker.java)
   - WorkManager worker for background processing
   - Manages the upload queue and retry logic
   - Handles both new SMS and historical sync

3. **WebhookUploader** (sms2webhook/src/main/java/org/golder/sms2webhook/WebhookUploader.java)
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

The payload is the SMS provider's own row, not a shape the app defines.
`SmsStoreWorker.encodeMessage` copies every column the cursor returns, so the
fields present depend on the device's messaging provider. On a typical device:

```json
{
  "_id": "2",
  "thread_id": "2",
  "address": "6505551212",
  "date": 1724154171042,
  "date_sent": 1724154170000,
  "protocol": "0",
  "read": "0",
  "status": "-1",
  "type": "1",
  "reply_path_present": "0",
  "body": "This is the message body",
  "locked": "0",
  "sub_id": "1",
  "error_code": "0",
  "creator": "com.google.android.apps.messaging",
  "seen": "1"
}
```

Consequences worth knowing before changing anything here:

- There is no `from`/`to`/`text`/`timestamp`/`sim` field. The sender is
  `address`, the body is `body`, the time is `date`, and the SIM is `sub_id` —
  and only where the provider exposes that column, which is not guaranteed for
  the `content://sms` URI this app queries.
- Values are strings, because `encodeMessage` reads every column with
  `cursor.getString`. A consumer must not assume a numeric JSON type.
- The query is inbox-only, using `Telephony.Sms.Inbox.CONTENT_URI`. It was the whole
  `sms` table until 2.2.0, which also uploaded sent messages and inflated every count
  the dashboard derived from them.
- The API key is **not** in the body. It goes out as an
  `Authorization: Bearer` header.

### Critical Files
- Permission handling: `MainActivity.requestSmsPermissionsIfNeeded`
- Inbox query and per-message decision: `SmsStoreWorker.doWork`
- Status classification: `SmsStoreWorkerStatusHandling`
- Webhook upload: `WebhookUploader`
- Database schema: `CacheDatabase.java`
- ViewModel implementation: `MainViewModel.java`
- Main screen status: `SyncStatus` — pure and Android-free so the mapping is unit tested
- Cache consistency findings: `CacheDiagnostics`

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
- Capture crash logs from a device with the `adb logcat` command above; there is no
  install-and-log helper script in this repository
- Test on physical devices (especially Pixel phones) in addition to emulators

### Known Device Compatibility
- ✅ Android emulators (API 28+)
- ✅ Pixel 8 (after minSdk and error handling fixes)
- ⚠️ Requires SMS permissions to be granted manually on first run

## CI/CD and Release Process

### Release Checklist

Work through these in order. Steps 1–3 must land on `master` **before** the tag is
pushed, because the tag is what the release reads them from.

**1. Bump the version** — `sms2webhook/build.gradle`

`versionCode` must increase on every release. Android refuses to install an APK at
an unchanged `versionCode`, so a forgotten bump publishes something nobody can
upgrade to.

`versionName` follows the convention this repo has already used: a **minor** bump
for functionality added (2.1.0 added QR setup pairing), a **patch** bump for
defect fixes alone (2.0.1 shipped an MVVM refactor as a patch).

Verify against the built artifact, not the source file — this is the check that
catches a typo the grep below will not:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleRelease
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AAPT2=$(printf '%s\n' "$SDK"/build-tools/*/aapt2 | sort -V | tail -n 1)
"$AAPT2" dump badging sms2webhook/build/outputs/apk/release/sms2webhook-release.apk | grep ^package
# expect versionCode=N versionName='X.Y.Z', matching what you just set
```

`release.yml` greps the version out of `build.gradle` with
`versionName "\K[^"]+`, so the value must keep its double quotes.

**2. Write the changelog entry** — `CHANGELOG.md`

**`zapstore.yaml` sets `release_notes: ./CHANGELOG.md`.** This file is the source
ZapStore reads for a release's notes. It is not decorative: releasing without an
entry publishes the *previous* release's notes, which has already happened once
here (v2.1.1 shipped with the newest entry still at 2.0.1).

Add the entry in the same PR as the version bump. Also note that
`release.yml:104` builds the *GitHub* release notes from `git log --pretty=format:
"- %s"`, so a squashed commit produces a one-line changelog on GitHub. Splitting a
large change into logical commits before tagging improves that.

**3. Green local checks**

```bash
./gradlew check   # unit tests and lint
```

**4. On-device smoke test**

Not optional for anything touching the sync, uploads, permissions or WorkManager.
None of that has automated coverage, and unit tests cannot catch a dropped SMS or a
blank window. Install with `./gradlew installDebug`, then check:

- a received SMS reaches the webhook
- the sync completes and the progress bar advances
- the counters move during a sync, and after Clear Cache
- denying either SMS permission leaves a usable window

**5. Merge through a PR**

Branch, PR, review, then a human merges. Never merge your own PR.

**6. Tag, and only then**

```bash
git tag -a vX.Y.Z -m "..." && git push origin vX.Y.Z
```

The trigger is `v[0-9]+.[0-9]+.[0-9]+`, so a prerelease such as `v2.2.0-rc1` is
deliberately excluded and will publish nothing.

**7. Confirm it actually published**

`release.yml` sets `skip-if-unconfigured: 'true'`, so **a green run does not prove
the Zapstore publish happened** — a missing or invalid `ZAPSTORE_SIGN_WITH` exits
success having published nothing.

Four kinds are published, and **the one that carries the APK is `3063`**. Check for
all of them, in this order:

```
APK resolved from ... sms2webhook-vX.Y.Z-release-signed.apk
org.golder.sms2webhook X.Y.Z (code N), certificate ... via v2
uploaded sms2webhook/src/main/ic_launcher-playstore.png (...)
published kind 32267 ...   release description
published kind 3063 ...    asset event - THE APK
published kind 30063 ...    metadata
```

**Do not accept `32267` as proof of a publish.** It is the release description, and it
is published first and independently of the asset event, so it succeeds in exactly the
runs that leave the listing broken. Two releases have now failed this way with
`32267` present in the log:

- v2.1.2 — the relay rejected the 3063 event for a missing `version_code` tag, and the
  action swallowed the rejection, so the run exited 0. The listing had a release event
  pointing at an asset the relay did not have: **Install greyed out**.
- v2.2.0 — the relay stopped responding partway through; the action gave up after
  about four seconds and logged `publish timed out`. No 3063, so the listing was in the
  same state again.

If the step fails, or if `published kind 3063` is absent from a green run, **re-run the
failed job** (`gh run rerun <id> --failed`) before cutting anything else. The v2.2.0
failure was transient and completed on the first re-run. The step is idempotent: it
republishes the release description and re-uploads the same content-addressed assets.

The `uploaded ic_launcher-playstore.png` line is how you confirm the listing icon
actually reached the CDN. Without an `icon:` field in `zapstore.yaml` the kind 32267
event carries no icon tag and the store shows a placeholder letter instead, which
looks like a client bug rather than a missing config field.

The resolved asset must be the **release-signed** APK. `zapstore.yaml` pins
`match: ".*-release-signed\\.apk$"` so the debug APK cannot be chosen, and both APKs
are deliberately attached to the GitHub release, so seeing `sms2webhook-*.apk`
listed there is expected and not a mistake.

If `ZAPSTORE_SIGN_WITH` is changed or rotated, run `verify-zapstore-signing.yml`
first: it signs every event but uploads and publishes nothing.
`zapstore-publisher.yml` separately proves the committed npub matches the signing
credential, and runs weekly. A mismatch there is refused by the relay with no
useful local symptom.

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