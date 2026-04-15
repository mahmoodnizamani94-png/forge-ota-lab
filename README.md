# Forge OTA Lab

Android-first mobile extraction workbench for Android OTA packages. Import OTA archives, classify them by support tier, extract verifiable partition images, and export artifacts — all local-first, on-device.

## Architecture

```
app/                          Kotlin + Jetpack Compose application shell
  ui/                         Compose screens, navigation, theme
  features/                   Feature modules (home, import, analysis, extraction, browser, settings)
  data/                       Room DB, DAOs, repositories
  domain/                     Domain models, use cases, orchestration
  workers/                    WorkManager workers
  permissions/                Runtime permission handling
  diagnostics/                Diagnostics export, event logging
  di/                         Hilt DI modules

core-extractor-rs/            Rust extraction core (compiled via cargo-ndk)
  crates/
    sniff/                    Format detection and classification
    payload/                  payload.bin parser, protobuf, extraction
    images/                   Standalone image handling
    filesystems/              Read-only filesystem parsers (ext4 for v1)
    verification/             SHA-256, constant-time comparison
    adapters/                 OTA family adapters
  forge-jni/                  JNI cdylib — the only crate with unsafe JNI boundary code

shared-contracts/             Pure Kotlin models shared between app and JNI bridge
  model/                      Domain types, sealed results, error hierarchies
  events/                     Structured event definitions

prd/                          Product Requirements Document and design tokens
  tokens/                     OKLCH color token files (primitives, semantic, components)
  build/css/                  Reference CSS output for the color system
```

## Build Prerequisites

### Required Tools

| Tool | Minimum Version | Install |
|:---|:---|:---|
| Android Studio | Ladybug (2024.2+) | [Download](https://developer.android.com/studio) |
| JDK | 17+ | Bundled with Android Studio |
| Rust | 1.85.0+ | `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \| sh` |
| cargo-ndk | Latest | `cargo install cargo-ndk` |
| Android NDK | r27d (27.2.12479018) | Via Android Studio SDK Manager |

### Rust Android Targets

```bash
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
```

### Environment Variables

Ensure `ANDROID_NDK_HOME` points to your NDK installation, or configure it in `local.properties`:

```properties
# local.properties (not checked in)
ndk.dir=/path/to/Android/sdk/ndk/27.2.12479018
```

## Building

### Full Debug Build (Android APK + Rust native library)

```bash
./gradlew :app:assembleDebug
```

This will:
1. Build the Rust native library via `cargo ndk` for `arm64-v8a` and `x86_64`
2. Place the `.so` files in the correct `jniLibs/` directories
3. Compile the Kotlin/Compose Android app
4. Produce a debug APK

### Rust Workspace Only

```bash
cd core-extractor-rs
cargo check --workspace    # Type check all crates
cargo test --workspace     # Run all tests
cargo build -p forge-jni   # Build just the JNI bridge (host target)
```

### Shared Contracts Only

```bash
./gradlew :shared-contracts:build
```

## Module Responsibilities

| Module | Type | Responsibility |
|:---|:---|:---|
| `:app` | Android Application | Compose UI, navigation, Hilt DI root, WorkManager, Room DB |
| `:core-extractor-rs` | Android Library | Rust native library + Kotlin JNI wrapper (NativeBridge) |
| `:shared-contracts` | Pure Kotlin (JVM) | Serializable domain models shared across JNI boundary |

## Technology Stack

| Layer | Technology |
|:---|:---|
| Language | Kotlin 2.1.21 |
| UI | Jetpack Compose + Material 3 |
| Theme | Custom OKLCH-native token system (no Material You dynamic color) |
| DI | Hilt |
| Persistence | Room (KSP) |
| Background Work | WorkManager |
| Native Core | Rust (edition 2024) via cargo-ndk |
| JNI Bridge | `jni` crate + JSON serialization |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle 8.11.1 + AGP 8.10.1 |

## Verifying the JNI Bridge

After installing the debug APK on a device or emulator:

```kotlin
// In any Composable or ViewModel:
val result = NativeBridge.ping()  // Returns "pong" if bridge works
val version = NativeBridge.coreVersion()  // Returns Rust crate version
```

## Troubleshooting

### `cargo-ndk is not installed`

The build will show a clear error box. Fix:

```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android x86_64-linux-android
```

### NDK not found

Set `ANDROID_NDK_HOME` or add to `local.properties`:

```properties
ndk.dir=/path/to/ndk/27.2.12479018
```

### JNI `UnsatisfiedLinkError`

Check that:
1. The Rust build completed (`core-extractor-rs/src/main/jniLibs/arm64-v8a/libforge_jni.so` exists)
2. The `NativeBridge` package matches the Rust function names
3. The APK contains the `.so` file (unzip and check `lib/arm64-v8a/`)

## License

Proprietary. See LICENSE file.
