# GitHub Actions Workflows

This directory contains GitHub Actions workflows for the SMS2Webhook Android application.

All workflows build with **JDK 21** (required by `sourceCompatibility`/`targetCompatibility`
in `sms2webhook/build.gradle`) and reference the Gradle module directory `sms2webhook/`,
not `app/`.

## Workflows

### 1. Android CI (`android.yml`)
- **Triggers**: Push to `master` or `develop` branches, Pull requests
- **Purpose**: Continuous Integration - builds, tests, and generates APKs
- **Outputs**:
  - Debug and Release APKs as artifacts
  - Lint results (HTML report plus error/warning counts in the job summary)
  - Build summary in GitHub UI
- **Features**:
  - Runs unit tests and lint checks
  - Generates both debug and release APKs
  - Names APKs with version, commit hash, and branch
  - Creates a summary report
- **Signing**: none. The release APK from this workflow is signed with the debug key.
  Only tagged releases (`release.yml`) are signed with the release key.

### 2. Release (`release.yml`)
- **Triggers**: Push of tags matching `v*` (e.g., `v1.2.0`)
- **Purpose**: Create official releases with signed APKs
- **Outputs**:
  - GitHub Release with changelog
  - Release APK signed with the release key, verified with `apksigner`
  - Debug APK for testing
- **Signing**: the keystore is base64-decoded to `$RUNNER_TEMP` and passed to Gradle,
  which signs the APK during `assembleRelease`. The keystore is deleted before the job ends.

### 3. Pull Request Check (`pr-check.yml`)
- **Triggers**: Pull request events (opened, synchronized, reopened)
- **Purpose**: Automated code review and quality checks
- **Features**:
  - Lint check with error/warning counts
  - APK size analysis
  - Security checks (hardcoded secrets, HTTP URLs)
  - Automated PR comment with results
- **Permissions**: `pull-requests: write`, required to post the summary comment.
  The job only runs for branches in this repository, not forks.

### 4. Manual Build (`manual-build.yml`)
- **Triggers**: Manual workflow dispatch
- **Purpose**: On-demand builds with customizable options
- **Options**:
  - Build type (debug/release/both)
  - Optional GitHub release creation
  - Custom release naming
- **Signing**: applied only when `create_release` is set, since draft releases are
  published later and should carry the release key.
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
   - `KEY_PASSWORD`: Your key password. Optional; defaults to `KEY_STORE_PASSWORD`

## Release Signing Configuration

`sms2webhook/build.gradle` reads these values from Gradle properties, or from
environment variables of the same name:

| Property | Required | Purpose |
|---|---|---|
| `RELEASE_STORE_FILE` | yes | Absolute path to the keystore |
| `RELEASE_STORE_PASSWORD` | yes | Keystore password |
| `RELEASE_KEY_ALIAS` | yes | Key alias |
| `RELEASE_KEY_PASSWORD` | no | Key password, defaults to `RELEASE_STORE_PASSWORD` |

Supplying only some of the required values fails the build with an explicit error rather
than silently falling back to debug signing. When none are supplied, `assembleRelease`
falls back to the debug signing config, which is what local builds and pull request
builds use.

**Do not commit `org.gradle.java.home` or any signing value to `gradle.properties`.**
Machine-specific paths there break CI. Put local overrides in `~/.gradle/gradle.properties`
instead, which Gradle reads in addition to the project file.

## Artifact Retention

- CI builds: 90 days (default)
- Manual builds: 30 days
- Release artifacts: Permanent (attached to GitHub releases)

## Tips

1. **Triggering Manual Builds**: Go to Actions → Manual Build → Run workflow
2. **Creating Releases**: Push a tag: `git tag v1.2.0 && git push origin v1.2.0`
3. **Debugging Failed Builds**: Check the workflow logs and download lint reports from artifacts
4. **APK Naming**: APKs are named with pattern: `sms2webhook-{version}-{type}-{commit/timestamp}.apk`

## Verifying Workflows

`actionlint` catches expression and context errors that YAML parsing misses:

```bash
actionlint -shellcheck= -pyflakes= .github/workflows/*.yml
```