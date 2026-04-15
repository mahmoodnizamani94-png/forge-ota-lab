# Forge OTA Lab — Release Operations Manual

This document covers everything a release manager needs to ship a closed beta APK to Google Play without asking a developer any questions.

---

## Table of Contents

1. [Pre-Release Checklist](#pre-release-checklist)
2. [Signing Setup](#signing-setup)
3. [Building the Release](#building-the-release)
4. [Upload to Play Store](#upload-to-play-store)
5. [Direct APK Distribution](#direct-apk-distribution)
6. [Rollback Triggers](#rollback-triggers)
7. [Rollback Procedure](#rollback-procedure)
8. [Support Matrix](#support-matrix)
9. [CI Pipeline Reference](#ci-pipeline-reference)

---

## Pre-Release Checklist

Complete every item before building a release. Any failure is a ship-blocker.

### Automated (CI)

- [ ] `rust-check` job passes — `cargo check`, `clippy -D warnings`, `test`, `fmt`
- [ ] `android-build` job passes — lint (zero errors), `assembleDebug`, unit tests
- [ ] `binary-size-check` job passes — release APK < 150 MB (PRD NFR-8)
- [ ] `beta-gate` job passes — corpus success rate ≥ 99.0% (PRD NFR-14)
- [ ] Room schema JSON exists in `app/schemas/` (auto-migration support)

### Manual

- [ ] Smoke test release APK on physical device:
  - Import a supported full OTA ZIP → analysis completes
  - Extract boot partition → SHA-256 verified
  - Import an unsupported file → Forensic mode shown
  - Cancel extraction → verified outputs preserved, temps cleaned
  - Resume interrupted job → continues from checkpoint
- [ ] Dark mode and light mode both render correctly
- [ ] TalkBack navigation works through core flow
- [ ] Crash reporting disabled by default — verified no network calls without consent
- [ ] Version code and version name updated in `app/build.gradle.kts`

---

## Signing Setup

### 1. Generate Keystore (First Time Only)

```bash
keytool -genkey -v \
  -keystore forge-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias forge-key \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=Forge OTA Lab, OU=Mobile, O=Forge, L=Unknown, ST=Unknown, C=US"
```

> **CRITICAL:** Store the keystore file and passwords in a secure location (password manager, HSM). Losing the keystore means you cannot update the app on Google Play — ever. There is no recovery process.

### 2. Configure Local Signing

Add to `~/.gradle/gradle.properties` (user-level, NOT project-level):

```properties
FORGE_KEYSTORE_PATH=/absolute/path/to/forge-release.jks
FORGE_KEYSTORE_PASSWORD=<your-store-password>
FORGE_KEY_ALIAS=forge-key
FORGE_KEY_PASSWORD=<your-key-password>
```

### 3. Configure CI Signing

Add these GitHub repository secrets:

| Secret Name | Value |
|------------|-------|
| `FORGE_KEYSTORE_BASE64` | `base64 -w0 forge-release.jks` output |
| `FORGE_KEYSTORE_PASSWORD` | Keystore password |
| `FORGE_KEY_ALIAS` | `forge-key` |
| `FORGE_KEY_PASSWORD` | Key password |

To encode the keystore:

```bash
base64 -w0 forge-release.jks > forge-release.jks.b64
# Copy content of forge-release.jks.b64 into FORGE_KEYSTORE_BASE64 secret
```

### 4. Verify Signing is NOT Checked In

```bash
# These must NOT exist in the repo:
git ls-files | grep -E '\.(jks|keystore)$'  # Should return empty
git ls-files | grep 'google-services.json'   # Should return empty
```

---

## Building the Release

### Local Release Build

```bash
# Build signed release APK + AAB
./gradlew assembleRelease bundleRelease

# Verify the APK is signed
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk

# Check APK size
ls -lh app/build/outputs/apk/release/app-release.apk
```

### CI Release Build (Recommended)

Push a version tag to trigger the release workflow:

```bash
# 1. Update version in app/build.gradle.kts
#    versionCode = <incremented integer>
#    versionName = "0.1.0"

# 2. Commit the version bump
git add app/build.gradle.kts
git commit -m "chore: bump version to 0.1.0"

# 3. Tag and push
git tag v0.1.0
git push origin main --tags
```

The `release.yml` workflow will:
1. Run full CI validation
2. Build signed release APK + AAB
3. Create a GitHub Release with both artifacts attached
4. Record binary size in release notes

---

## Upload to Play Store

### First-Time Setup

1. Create app in [Google Play Console](https://play.google.com/console)
2. Set up store listing using content from `store/listing.md`
3. Complete content rating questionnaire (see `store/listing.md` content rating notes)
4. Set up privacy policy URL: `https://forgeotalab.dev/privacy`
5. Enable closed beta track under **Testing → Closed testing**
6. Create a testers list and add beta testers

### Upload Process

1. Download the AAB from the GitHub Release page
2. In Play Console → **Testing → Closed testing → Manage track**
3. Click **Create new release**
4. Upload the `.aab` file
5. Add release notes from `store/release-notes/<version>.md`
6. Set rollout percentage:
   - Closed beta: 100% of testers
   - Public beta: start at 10%, increase per rollout controls
7. Review and submit

> **NOTE:** APK is for direct distribution (GitHub, sideloading). AAB is for Play Store. Always upload AAB to Play.

---

## Direct APK Distribution

For users outside the Play Store beta:

1. Download the signed release APK from the GitHub Release page
2. Host at the distribution site
3. Users install via Settings → Install Unknown Apps

---

## Rollback Triggers

From PRD §Rollback Triggers — any of these triggers an immediate rollback:

| # | Trigger | Threshold | Window |
|---|---------|-----------|--------|
| 1 | Crash-free sessions drop | < 99.0% | Rolling |
| 2 | Wrong-output reports spike | > 2% for any family | 7-day |
| 3 | Failure rate spike after adapter update | > 10 percentage points from baseline | Per family |
| 4 | Single error type spike | 3× above baseline | Rolling |

---

## Rollback Procedure

From PRD §Rollback Procedure — execute in order:

### Step 1: Revoke Failing Adapter Version

Update the signed adapter manifest to revoke the failing adapter version. Clients will stop using it on next manifest refresh (≤ 24 hours, or manual refresh from Settings).

```json
{
  "revocations": [
    {
      "adapter_id": "<failing-adapter-id>",
      "version": "<failing-version>",
      "reason": "<human-readable-reason>"
    }
  ]
}
```

### Step 2: Disable Affected Family Flag

Set the family feature flag to `false` in the signed manifest. This prevents new extraction jobs for the affected family while preserving analysis capability.

### Step 3: Preserve User Data

**Do NOT clear:**
- Analyzed packages
- Job history
- Verified extraction outputs
- User preferences

### Step 4: Mark Affected Jobs as Retryable

Jobs that were in-progress when the adapter was revoked should be marked as retryable under the downgraded adapter version. Users see: "This job was interrupted due to an adapter update. Tap to retry with the previous version."

### Step 5: Emergency App Update (If Manifest-Level Rollback is Insufficient)

If the issue is in app-level code (not adapter-specific):

1. Revert the offending commit
2. Bump `versionCode` (must be higher than the broken release)
3. Build and upload emergency release
4. Set rollout to 100% with high priority

---

## Support Matrix

### Supported at Launch

| Family | Tier | Capabilities |
|--------|------|-------------|
| Google / Pixel full payload-based OTA | **Supported** | Full analysis, partition selection, verified extraction, filesystem browse |
| Standard payload.bin family | **Supported** | Full analysis and verified extraction |
| Standalone boot-critical images | **Supported** | Import, fingerprint, export, metadata |

### Experimental at Launch

| Family | Tier | Capabilities |
|--------|------|-------------|
| Incremental payload-based OTAs | **Experimental** | Prerequisite wizard, base validation, reconstruction (labeled `reconstructed`) |
| OEM payload-based variants | **Experimental** | Family-specific extraction with experimental labeling |

### Forensic at Launch

| Family | Tier | Capabilities |
|--------|------|-------------|
| Unknown payload-based packages | **Forensic** | Fingerprint, metadata, diagnostics export |
| Archives with OTA markers but unsupported path | **Forensic** | Inspection only, no extraction CTA |

### Not Supported at Launch

- Samsung Odin/AP-BL-CP-CSC tar workflows (planned: v1.1)
- Xiaomi legacy A-only image zip workflows
- Any format requiring unsigned community adapters

---

## CI Pipeline Reference

### Workflows

| Workflow | File | Trigger |
|----------|------|---------|
| CI | `.github/workflows/ci.yml` | PR, push to main, manual |
| Release | `.github/workflows/release.yml` | Tag push `v*.*.*` |

### CI Jobs

| Job | Runs On | What |
|-----|---------|------|
| `rust-check` | PR + push | cargo check, clippy, test, fmt |
| `android-build` | PR + push | lint, assembleDebug, unit tests, Room schema |
| `android-instrumented` | push to main | Connected tests with emulator |
| `binary-size-check` | PR + push | Release APK size enforcement |
| `beta-gate` | Manual only | Corpus integration tests, 99% pass rate gate |

### Required GitHub Secrets

| Secret | Purpose |
|--------|---------|
| `FORGE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `FORGE_KEYSTORE_PASSWORD` | Keystore password |
| `FORGE_KEY_ALIAS` | Key alias (e.g., `forge-key`) |
| `FORGE_KEY_PASSWORD` | Key password |

---

## Version History

| Version | Date | Type | Notes |
|---------|------|------|-------|
| 0.1.0 | — | Closed Beta | Initial release. See `store/release-notes/0.1.0.md`. |
