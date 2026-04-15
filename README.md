# Forge OTA Lab 🔩

**Android OTA Extraction Workbench** — a local-first, on-device tool for importing Android OTA update packages, classifying them by support tier, extracting verifiable partition images, and exporting artifacts.

> ⚠️ **v0.1.0 Closed Beta** — This is a pre-release build. Expect rough edges. Report issues via GitHub Issues.

---

## What It Does

Forge OTA Lab takes Android OTA (Over-The-Air) update archives — the ZIP files that update your phone — and lets you:

- **Import** OTA packages directly on your Android device via file picker or share intent
- **Analyze** package contents with automatic format detection and truthful support tier classification
- **Extract** individual partition images (boot, system, vendor, etc.) with SHA-256 verification
- **Browse** extracted filesystem contents with a read-only ext4 parser
- **Export** verified artifacts to any SAF-accessible location
- **Resume** interrupted extractions from checkpoints — verified outputs survive process death

All processing happens **locally on your device**. No cloud, no uploads, no network required.

---

## Support Tiers

Every imported package is classified by the Rust core — the UI never guesses:

| Tier | What It Means | Capabilities |
|:-----|:--------------|:-------------|
| 🟢 **Supported** | Google Pixel & standard payload-based full OTAs | Full analysis, partition selection, verified extraction, filesystem browse |
| 🟡 **Experimental** | Incremental OTAs, OEM payload variants | Prerequisite wizard, base validation, reconstruction (labeled `reconstructed`) |
| 🔵 **Forensic** | Unknown/unrecognized formats | Metadata inspection, diagnostics export — no extraction, no green CTA |

> Unknown formats **never** map to success visuals. An unrecognized file renders in Forensic mode with informational treatment only.

---

## Key Features

- **Truthful classification** — support tier comes exclusively from the Rust core's analysis, never from the UI
- **Verification-first** — no "Extraction complete" until SHA-256 verification passes. Always.
- **Partial success** — if 5 of 7 partitions verify but 2 fail, verified outputs remain available
- **Cancellation safety** — cancelling cleans temp files but never deletes completed, verified partitions
- **Process death resilience** — WorkManager + Room checkpoints survive app kills and device reboots
- **Single-job guard** — only one extraction runs at a time; analysis can proceed in parallel
- **Consent-gated telemetry** — crash reporting disabled by default, enabled only with explicit user opt-in
- **OKLCH-native theme** — custom color system with full dark/light mode support
- **Accessibility** — semantic content descriptions, heading navigation, 48dp touch targets, TalkBack tested

---

## Architecture

```
app/                          Kotlin + Jetpack Compose application
  ui/                         Compose screens, navigation, OKLCH theme system
  data/                       Room database, DAOs, repositories
  domain/                     Use cases, preflight validation, orchestration
  workers/                    WorkManager (extraction, history purge, manifest refresh)
  di/                         Hilt dependency injection modules
  diagnostics/                Structured event logging, diagnostics export

core-extractor-rs/            Rust extraction core (compiled via cargo-ndk)
  crates/
    sniff/                    Format detection (magic bytes, classification)
    payload/                  payload.bin parser, protobuf, extraction ops
    images/                   Standalone image import/export
    filesystems/              Read-only ext4 filesystem parser
    verification/             SHA-256, constant-time comparison
    adapters/                 OTA family adapters (Pixel, standard payload)
  forge-jni/                  JNI cdylib — ONLY crate with unsafe boundary code

shared-contracts/             Pure Kotlin models shared across JNI boundary
  model/                      Domain types, sealed results, error hierarchies
  events/                     Structured event definitions
```

---

## Security Model

This is a tool that processes **untrusted binary input**. Security is structural, not optional:

| Threat | Defense |
|:-------|:--------|
| Manifest bomb | ≤ 64 MB allocation, 10-second timeout |
| Integer overflow | All extent calculations use `checked_mul` / `checked_add` |
| Decompression bomb | Abort if `bytes_written > declared_size × 1.01`, checked per chunk |
| Path traversal | Canonicalize output paths, reject if resolved path escapes destination |
| ZIP bomb | 1,000-entry limit on central directory scan, no recursive extraction |
| JNI panic | `catch_unwind` on every exported function — no Rust panic reaches the app |
| Manifest signature | Ed25519 verification before any field access, public key pinned in binary |
| Hash comparison | SHA-256 verification uses constant-time comparison, not `==` |

---

## Tech Stack

| Layer | Technology |
|:------|:-----------|
| Language | Kotlin 2.1+ |
| UI | Jetpack Compose + Material 3 |
| Theme | Custom OKLCH-native token system (Material You OFF) |
| DI | Hilt |
| Persistence | Room (KSP, additive migrations only) |
| Background | WorkManager |
| Native Core | Rust (edition 2024) via cargo-ndk |
| JNI Bridge | `jni` crate + JSON serialization |
| Protobuf | prost (Rust) / protobuf-javalite (Kotlin) |
| Async | Kotlin Coroutines + Flow |
| Targets | API 35 (target), API 30 (min) |

---

## Build Prerequisites

| Tool | Version | Install |
|:-----|:--------|:--------|
| Android Studio | Ladybug 2024.2+ | [developer.android.com/studio](https://developer.android.com/studio) |
| JDK | 17+ | Bundled with Android Studio |
| Rust | 1.85.0+ | [rustup.rs](https://rustup.rs) |
| cargo-ndk | Latest | `cargo install cargo-ndk` |
| Android NDK | r26d+ | Via Android Studio SDK Manager |
| protoc | 29.x+ | [protobuf releases](https://github.com/protocolbuffers/protobuf/releases) |

### Rust Android Targets

```bash
rustup target add aarch64-linux-android x86_64-linux-android
```

### Environment Variables

```bash
export ANDROID_NDK_HOME=/path/to/Android/sdk/ndk/26.1.10909125
export PROTOC=/path/to/protoc
```

---

## Building

### Debug APK

```bash
./gradlew assembleDebug
```

This compiles Rust native libraries for `arm64-v8a` and `x86_64`, builds the Compose UI, and produces a debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

### Release APK (requires signing keystore)

```bash
./gradlew assembleRelease bundleRelease
```

See [RELEASE.md](RELEASE.md) for signing setup, CI configuration, and Play Store upload instructions.

### Rust Workspace Only

```bash
cd core-extractor-rs
cargo check --workspace     # Type check
cargo test --workspace      # Run tests
cargo clippy --workspace    # Lint
```

---

## What This App Does NOT Do

These are explicit non-goals for v1:

1. ❌ Flash firmware to a device
2. ❌ Unlock bootloaders or provide root guidance
3. ❌ Patch boot images or integrate Magisk
4. ❌ Author, repack, or re-sign OTA packages
5. ❌ Download OEM firmware from the internet
6. ❌ Claim universal support for proprietary/encrypted formats
7. ❌ iOS support
8. ❌ Desktop companion
9. ❌ Cloud extraction

---

## Project Status

| Component | Status |
|:----------|:-------|
| Compose UI (10+ screens) | ✅ Complete |
| Rust extraction core (7 crates) | ✅ Complete |
| JNI bridge with panic containment | ✅ Complete |
| Room persistence + checkpointing | ✅ Complete |
| WorkManager job orchestration | ✅ Complete |
| OKLCH theme system (dark + light) | ✅ Complete |
| Accessibility audit (WCAG 2.2 AA) | ✅ Complete |
| Integration test suite | ✅ Complete |
| CI/CD pipeline (GitHub Actions) | ✅ Complete |
| R8/ProGuard hardening | ✅ Complete |
| Firebase Crashlytics (opt-in) | ✅ Complete |
| Play Store metadata | ✅ Ready |

---

## Contributing

This is currently a closed-source project in closed beta. If you're interested in contributing, open an issue to discuss.

## License

All rights reserved. See [LICENSE](LICENSE) for details.
