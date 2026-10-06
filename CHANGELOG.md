# Changelog

## [2.2.0] - 2026-10-06

Minor: the main screen is redesigned, and the app now uploads only what you
received. No longer sending your sent messages is a deliberate behaviour change.

### 🎨 Main screen
- **One status line instead of three counters.** The screen showed Inbox / Uploaded /
  Failed plus a progress bar, which answered "what has happened in my history"
  rather than "is there anything outstanding". On a phone holding 624 messages with
  an empty cache it read Uploaded 0, Failed 0 — indistinguishable from a fully synced
  device.
- The status line now reports one of four states: all uploaded, N not uploaded yet,
  N refused, or syncing. The colour reflects urgency, so an ordinary unsynced phone
  is not alarmed at.
- Sync progress moved onto the status line (`Syncing… 42%`) rather than a separate bar.
- **Clear cache moved to the overflow menu.** It is destructive and rarely needed.

### 🐛 Fixes
- **The sync queued from the menu cleared the cache without asking.** The confirmation
  dialog only existed on the button that has just been removed; the menu item has been
  present all along and went straight to the destructive action.
- **The Clear cache dialog said the opposite of the truth** — "Sync SMS first to ensure
  all messages are uploaded". Clearing the cache *causes* a full re-upload. It now
  explains that, and that whether duplicates appear depends on the server recognising
  them.
- **Repeated diagnostics filled the activity log with one line.** Four copies of
  "624 messages have no cache entry" were visible at rest on a real device, because
  diagnostics run on every activity launch, every Clear cache and every sync exit. A
  finding is now reported once and stays quiet while it remains true; a count that
  changes reports the new number.

### 🔒 Behaviour
- **The sync is inbox-only.** The query was the whole `sms` table, so sent messages
  were uploaded as well. That is not what the app promises to do, it inflated every
  count derived from it, and it made inbox-minus-uploaded drift positive by roughly the
  size of your sent history. **Existing installs will show fewer messages immediately.**

### 🧪 Tests
- 143 unit tests, up from 129. Added `SyncStatusTest` and `CacheDiagnosticsTest`,
  covering the status mapping and the report-once rule. `StatisticsTest` was removed
  with the counters it tested.

---

## [2.1.2] - 2026-10-06

Documentation only. **No application code changed since 2.1.1**, so the APK is
functionally identical; only `versionCode` and `versionName` differ.

Published so that the ZapStore listing carries corrected text. The listing is a
signed Nostr event with the description and screenshots baked in, and
`zapstore.yaml` and this file are inputs to publishing rather than the source of
truth — so the 2.1.1 listing kept its original wording until a new publish replaced
the event.

### 📚 Documentation
- **Corrected the ZapStore listing, which promised a SIM slot that may not be
  sent.** It said every payload includes "the SIM slot it arrived on".
  `SmsStoreWorker.encodeMessage` copies every column the cursor returns from
  `content://sms`, so `sub_id` is present only where the device's messaging
  provider exposes it. The listing now states the actual contract: the payload is
  the provider's own row, its shape varies by device, and only `address`, `body`
  and `date` are dependable.
- **Added the missing 2.1.1 entry**, which this release had shipped without.
- Added a release checklist to `AGENTS.md`, covering the version bump, the
  changelog entry, the on-device smoke test, PR review, tagging, and how to confirm
  the Zapstore publish actually happened.
- `AGENTS.md` referenced a `debug_install.sh` script that does not exist in this
  repository; the guidance now points at `adb logcat`.
- The `## Version 2.0.0 Updates (Current)` heading no longer claims to be current.

---

## [2.1.1] - 2026-10-06

Patch release. Nothing here adds functionality and the webhook payload is
unchanged; every change corrects a defect.

### 🐛 Fixes — message loss and duplicate uploads
- **Refused messages were re-uploaded on every sync, forever.** A 4xx was written to
  the digest cache but never consulted: the "have we sent this?" check compared the
  cached status against an exact `200`. Any 2xx that was not 200 (`201`, `204`) and
  any 3xx had the same fate.
- **Overlapping workers uploaded the same messages.** Every incoming SMS enqueues a
  sync and the toolbar can start one, all as independent work requests, so
  WorkManager ran them in parallel against a single shared watermark. Syncs are now
  unique work chained with `APPEND_OR_REPLACE`.
- **A 401 or 404 was cached as a final outcome**, so a mistyped API key or webhook
  URL marked an entire backlog permanently refused and none of it was ever sent.
  Both are retried now, and the first one aborts the sync.
- **The dedup digest covered the whole provider row**, so `read`/`seen` flipping
  when a message is opened re-uploaded it, and per-install `_id` renumbering made a
  restored device re-upload its entire history. It now covers only
  `address`/`date_sent`/`body`/`thread_id`, matching the collector's server-side hash.

### 🐛 Other fixes
- Statistics counters were computed once at process start and never refreshed, so
  the dashboard did not move during a sync and clearing the cache appeared to do
  nothing.
- The activity log was mutated and copied from three threads concurrently, which
  throws. It is now bounded and copied under a lock.
- Denying either SMS permission left a blank window with no toolbar, no settings and
  no way to ask again. The UI is now always built.
- Webhook requests had no timeouts. The platform default is zero, meaning no limit,
  so a server that accepted the connection and never replied blocked the worker.
- The sync was enqueued from a main-looper post inside `onReceive`, which a
  manifest-declared receiver has no guarantee of running, so an SMS could be dropped.
- `moveToPosition` failure returned `failure()`, permanently wedging a sync that a
  deleted message had shortened.
- `Content-Type` was the malformed `application/json; utf-8`.

### 🔒 Security
- **The webhook API key was included in any `adb backup`**, being held in cleartext
  in the default preference store with `allowBackup` defaulting to true. Backup and
  device-to-device transfer are now both excluded.
- The `Application` held a strong reference to a destroyed `Activity`, along with
  its view tree and ViewModel.

### 📚 Documentation
- `CLAUDE.md` documented a webhook payload of `from`/`to`/`text`/`timestamp`/`sim`.
  None of those fields exist. Corrected to the payload actually sent.
- This entry. `zapstore.yaml` reads this file for store release notes, so an out-of-date
  file publishes out-of-date notes.

### 🧪 Tests
- 129 unit tests, up from 74. New coverage for the deduplication rule across
  repeated syncs, the message digest, the activity log bound, and the status
  classification.

---

## [2.0.1] - 2025-07-04

### 🐛 Fixes
- Fixed edge-to-edge display issues on Pixel 8 Pro and other modern devices
- Corrected window inset handling for the system status bar
- Removed a deprecated import in `SmsBroadcastReceiver`

### 📚 Documentation
- Expanded the README with full webhook payload documentation
- Added screenshots showing the main and settings screens

## [2.0.0] - 2024-01-03

### 🎨 UI/UX Improvements
- Complete Material Design 3 overhaul with modern components
- New card-based layout with better visual hierarchy
- Added dark mode support
- Improved statistics display with color-coded metrics
- RecyclerView-based activity log with timestamps and status icons
- Pull-to-refresh functionality
- Responsive design that adapts to all screen sizes

### 🏗️ Architecture
- Implemented MVVM architecture with ViewModels and LiveData
- Proper separation of concerns
- Background operations moved off main thread
- Added proper lifecycle management

### ✨ New Features
- Test Connection button in settings to verify webhook configuration
- Diagnostic system to detect cache inconsistencies
- Real-time input validation for webhook URL
- Swipe refresh for activity logs
- Material Design buttons and progress indicators
- Improved error messages and user feedback

### 🐛 Bug Fixes
- **Fixed cache inconsistency issue** where duplicate entries caused incorrect counts
- Database now uses message hash as primary key to prevent duplicates
- Added database migration to clean up existing duplicates
- Fixed potential memory leaks with proper lifecycle handling
- Resolved threading issues with database operations

### 🔧 Technical Improvements
- Upgraded to Room database version 2 with migration
- Added AndroidX lifecycle components
- Implemented proper dependency injection patterns
- Updated all dependencies to latest stable versions
- Added comprehensive GitHub Actions CI/CD

### 📝 Database Changes
- **Migration Required**: Database schema updated from v1 to v2
- Primary key changed from auto-increment ID to message hash
- Automatic cleanup of duplicate entries during migration
- INSERT OR REPLACE strategy to prevent future duplicates

### 🚀 Performance
- Optimized log display with RecyclerView
- Limited log entries to prevent memory issues
- Improved database query efficiency
- Better resource management

### 📦 Dependencies Updated
- Material Design Components
- AndroidX Lifecycle
- Room Database
- RecyclerView
- SwipeRefreshLayout

---

## [1.3.1] - Previous Release
- Basic SMS to webhook functionality
- Simple UI with basic statistics
- Cache system for tracking sent messages