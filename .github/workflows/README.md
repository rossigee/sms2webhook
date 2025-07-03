# GitHub Actions Workflows

This directory contains GitHub Actions workflows for the SMS2Webhook Android application.

## Workflows

### 1. Android CI (`android.yml`)
- **Triggers**: Push to `master` or `develop` branches, Pull requests
- **Purpose**: Continuous Integration - builds, tests, and generates APKs
- **Outputs**: 
  - Debug and Release APKs as artifacts
  - Lint results
  - Build summary in GitHub UI
- **Features**:
  - Runs unit tests and lint checks
  - Generates both debug and release APKs
  - Names APKs with version, commit hash, and branch
  - Creates a summary report

### 2. Release (`release.yml`)
- **Triggers**: Push of tags matching `v*` (e.g., `v1.2.0`)
- **Purpose**: Create official releases with signed APKs
- **Outputs**:
  - GitHub Release with changelog
  - Signed release APK (if signing keys configured)
  - Debug APK for testing
- **Required Secrets** (for APK signing):
  - `SIGNING_KEY`: Base64 encoded keystore
  - `ALIAS`: Key alias
  - `KEY_STORE_PASSWORD`: Keystore password
  - `KEY_PASSWORD`: Key password

### 3. Pull Request Check (`pr-check.yml`)
- **Triggers**: Pull request events (opened, synchronized, reopened)
- **Purpose**: Automated code review and quality checks
- **Features**:
  - Lint check
  - APK size analysis
  - Security checks (hardcoded secrets, HTTP URLs)
  - Automated PR comment with results

### 4. Manual Build (`manual-build.yml`)
- **Triggers**: Manual workflow dispatch
- **Purpose**: On-demand builds with customizable options
- **Options**:
  - Build type (debug/release/both)
  - Optional GitHub release creation
  - Custom release naming
- **Use Cases**:
  - Testing specific commits
  - Creating preview builds
  - Emergency releases

## Dependabot Configuration

The `.github/dependabot.yml` file configures automatic dependency updates:
- **Gradle dependencies**: Weekly checks on Mondays
- **GitHub Actions**: Weekly updates
- **Grouped updates**: AndroidX and AWS dependencies are grouped

## Setting Up Secrets

To enable APK signing in the release workflow:

1. Generate a keystore (if you don't have one):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias sms2webhook -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Convert keystore to base64:
   ```bash
   base64 -w 0 release.keystore > keystore.txt
   ```

3. Add secrets in GitHub repository settings:
   - `SIGNING_KEY`: Contents of keystore.txt
   - `ALIAS`: Your key alias (e.g., "sms2webhook")
   - `KEY_STORE_PASSWORD`: Your keystore password
   - `KEY_PASSWORD`: Your key password

## Artifact Retention

- CI builds: 90 days (default)
- Manual builds: 30 days
- Release artifacts: Permanent (attached to GitHub releases)

## Tips

1. **Triggering Manual Builds**: Go to Actions → Manual Build → Run workflow
2. **Creating Releases**: Push a tag: `git tag v1.2.0 && git push origin v1.2.0`
3. **Debugging Failed Builds**: Check the workflow logs and download lint reports from artifacts
4. **APK Naming**: APKs are named with pattern: `sms2webhook-{version}-{type}-{commit/timestamp}.apk`