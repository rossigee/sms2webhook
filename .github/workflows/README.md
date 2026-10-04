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
- **Zapstore**: a final step publishes the signed APK to the
  [Zapstore](https://zapstore.dev) Nostr relay. See
  [Publishing to Zapstore](#publishing-to-zapstore). The step is a no-op unless
  `ZAPSTORE_SIGN_WITH` is set.

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

### 5. Verify Zapstore signing (`verify-zapstore-signing.yml`)
- **Triggers**: Manual dispatch, or pushes to `master` touching `zapstore.yaml`
- **Purpose**: Prove `ZAPSTORE_SIGN_WITH` reaches the runner and signs valid
  events, without publishing anything
- **Mode**: `sign`, which builds and signs all three NIP-82 events but uploads
  no media and publishes nothing
- **Use**: Run after changing the secret, and again after moving to a bunker
  credential, to confirm the new credential works before it is trusted with a
  real release
- **Requires**: at least one published GitHub Release, since the action resolves
  the APK from the newest release asset

## Dependabot Configuration

The `.github/dependabot.yml` file configures automatic dependency updates:
- **Gradle dependencies**: Weekly checks on Mondays
- **GitHub Actions**: Weekly updates
- **Grouped updates**: AndroidX and AWS dependencies are grouped

## Setting Up Secrets

To enable APK signing in the release workflow:

1. Generate a keystore (if you don't have one):
   ```bash
   keytool -genkey -v -keystore release.keystore -storetype PKCS12 \
     -alias sms2webhook -keyalg RSA -keysize 2048 -validity 10000
   ```
   PKCS12 is required: Gradle reads the keystore as PKCS12, so a legacy JKS file
   fails with "Tag number over 30 is not supported".

2. Convert keystore to base64:
   ```bash
   base64 -w 0 release.keystore > keystore.txt
   ```

3. Add secrets in GitHub repository settings:
   - `SIGNING_KEY`: Contents of keystore.txt
   - `ALIAS`: Your key alias (e.g., "sms2webhook")
   - `KEY_STORE_PASSWORD`: Your keystore password
   - `KEY_PASSWORD`: Your key password. Optional; defaults to `KEY_STORE_PASSWORD`

## Publishing to Zapstore

[Zapstore](https://zapstore.dev) is an open Android app store built on Nostr.
Publishing is done by [`rossigee/zapstore-publish`](https://github.com/rossigee/zapstore-publish),
a GitHub Action that implements the NIP-82 catalog events, APK introspection and
Blossom upload in TypeScript. It needs no Go toolchain and no `zsp` binary.

There are no listing fees and no review queue.

### Verifying before publishing

Run the **Verify Zapstore signing** workflow first. It uses `mode: sign`, which
signs every event but publishes nothing and uploads nothing, so it proves the
credential works without writing to the shared relay or CDN.

### How the release pipeline is wired

`release.yml` calls the action as its last step, after the GitHub Release exists,
because the APK is resolved from the newest release asset. The credential is
passed through `env:` rather than `with:` so it never appears in the workflow
file, and the action masks it immediately with `::add-mask::`.

`skip-if-unconfigured: 'true'` keeps releases green before a Zapstore identity
exists. Remove it once publishing should happen on every tag.

### The `ZAPSTORE_SIGN_WITH` secret

Accepts either an `nsec1...` key or a NIP-46 `bunker://` URL. It is stored as the
`ZAPSTORE_SIGN_WITH` repository secret, which GitHub makes write-only: the value
cannot be read back, locally or through the API.

The config's `pubkey` does not need to be filled in by hand. The action derives
the npub from the credential at publish time and writes it into `zapstore.yaml`,
so the committed config cannot drift away from the key the relay verifies. A
bunker credential is the exception, because the identity behind one is only known
after connecting; in that case the config must carry its own `pubkey`.

Prefer a bunker for the long term. With an `nsec`, any process in the job —
including every third-party `uses:` action — can read `/proc/<pid>/environ`, and a
leak means losing that npub across all of Nostr, not just this listing. A bunker
URL is a revocable, scopable capability token: rotate the secret to revoke access
without rotating the identity. See the
[action's README](https://github.com/rossigee/zapstore-publish#signing-use-a-bunker-not-a-key)
for how to stand one up.

### Note on SMS permissions

Zapstore's blocking policy names apps that "exfiltrate user data without
consent". This app's purpose is forwarding SMS to a user-configured endpoint,
which is legitimate, but `READ_SMS` will draw scrutiny. The `description` in
`zapstore.yaml` therefore states plainly what is transmitted and that there are
no third-party servers. Keep that accurate if the data flow ever changes.

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