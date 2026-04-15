# AGENTS.md — Forge OTA Lab Agent Operating Contract

This file is the operating contract for AI agents working on Forge OTA Lab. It contains the repo structure, canonical document pointers, quality bar, and non-negotiable rules that agents must follow.

## Source of Truth

`prd/PRD-ForgeOTALab.md` is the finalized production PRD. When this document and any other source conflict, **the PRD wins**. Read the relevant PRD section before writing code for any feature.

### Companion Design Files

| File | Role |
|:---|:---|
| `prd/tokens/primitives.json` | Raw OKLCH color scales |
| `prd/tokens/semantic.json` | Light/dark semantic color mappings |
| `prd/tokens/components.json` | Component-level design token recipes |
| `prd/build/css/variables.css` | Reference CSS output for the color system |

## Repository Map

```
app/                          :app — Android application (Kotlin + Compose)
  src/main/kotlin/dev/forgeotalab/
    ui/theme/                 Forge theme (Color.kt, Type.kt, Theme.kt)
    ui/screens/               Compose screens
    di/                       Hilt DI modules
    ForgeApplication.kt       @HiltAndroidApp
    MainActivity.kt           @AndroidEntryPoint, single-activity

core-extractor-rs/            :core-extractor-rs — Rust extraction core
  crates/
    sniff/                    Format detection (magic bytes, classification)
    payload/                  payload.bin parser, DeltaArchiveManifest, extraction ops
    images/                   Standalone image import/export
    filesystems/              Read-only FS parsers (ext4 v1, EROFS v1.3)
    verification/             SHA-256, constant-time comparison
    adapters/                 OTA family adapters (Pixel, standard payload)
  forge-jni/                  JNI cdylib — ONLY crate with unsafe boundary code

shared-contracts/             :shared-contracts — Pure Kotlin models
  src/main/kotlin/dev/forgeotalab/contracts/
    model/                    JniResult, SupportTier, DerivationType, AnalysisResult
    events/                   ForgeEvent structured event definitions
```

## Architecture Non-Negotiables

The module layout in the repo map is **non-negotiable**. Do not flatten, merge, or restructure without explicit approval.

### Technology Stack

| Layer | Technology | Constraint |
|:---|:---|:---|
| Language | Kotlin 2.0+ | No Java source files |
| UI | Jetpack Compose + Material 3 | No XML layouts. No View system. |
| Theme | Custom OKLCH-native token system | Dynamic color (Material You) OFF |
| DI | Hilt | No manual DI, no Koin |
| Persistence | Room (KSP) | No KAPT. Additive migrations only. |
| Background | WorkManager | No raw Services for long-running jobs |
| Native core | Rust (edition 2024) via cargo-ndk | No C/C++ in core logic |
| JNI bridge | `jni` crate + JSON serialization | Results cross JNI as serialized JSON |
| Protobuf | prost (Rust) / protobuf-javalite (Kotlin) | Not protobuf-java, not wire |
| Async | Kotlin Coroutines + Flow | No RxJava. No LiveData in new code. |
| Targets | API 35 (target), API 30 (min) | |

## Quality Bar

### Kotlin/Compose
A principal Android engineer at Square would review this code and approve it without requesting structural changes:
- ViewModels use `StateFlow` with sealed UI state classes
- Repositories abstract data sources behind domain interfaces
- Compose screens use the theme system — no hardcoded colors, dimensions, or strings
- Error states are specific and actionable, never generic "Something went wrong"

### Rust
A systems engineer at Cloudflare would review this parser and find it safe against adversarial input:
- All arithmetic on untrusted values uses checked operations
- All decompression is bounded
- All JNI exports catch panics
- No `unsafe` outside the JNI boundary
- Errors carry context — `FormatError::UnsupportedVersion { found: 2, expected: 1 }`

### JNI Integration
- Results are serialized as JSON — less efficient than direct JNI types, but verifiable and debuggable
- Every JNI function wraps its body in `std::panic::catch_unwind`
- No Rust panic ever reaches the Android app shell

## Non-Negotiable Implementation Rules

_These come directly from the PRD. Violating any of them is a ship-blocking defect._

1. **UI support tier comes only from Rust core adapter output.** The Kotlin UI never decides whether a package is Supported, Experimental, or Forensic — that classification comes exclusively from the Rust core's analysis result.

2. **No extraction starts before storage and permission validation complete.** Preflight checks (storage budget, SAF permission, adapter readiness) must pass before any extraction job is created.

3. **No success state before verification completes.** No green badge, no checkmark, no "Extraction complete" message until SHA-256 verification passes. A passing extraction with a failing verification is NOT success.

4. **Unknown formats never map to success visuals.** An unrecognized file renders in Forensic mode with informational treatment. Never a green CTA. Never "Ready to extract."

5. **Incremental outputs always carry `derivation_type = reconstructed` or `raw_unverified`.** Never label a reconstructed partition as a direct extract.

6. **Adapter expansion does not change the public support promise** unless the manifest tier changes and the corpus gate passes.

7. **Only one extraction job active at a time.** Analysis can proceed in parallel.

8. **All output paths canonicalized and validated against SAF destination before write.**

## Security Boundaries

These are structural requirements, not optional hardening:

- **Bounded manifest parse:** ≤ 64 MB allocation. 10-second timeout.
- **Integer overflow:** All extent calculations use `checked_mul` / `checked_add`.
- **Decompression bomb:** Abort if `bytes_written > declared_size × 1.01`. Checked per chunk, not at end.
- **Path traversal:** Canonicalize output paths. Reject if resolved path escapes destination directory.
- **ZIP bomb:** 1,000-entry limit on central directory scan. No recursive extraction.
- **JNI panic isolation:** `catch_unwind` on every exported function. Return serialized error, never propagate panic.
- **Manifest signature:** Ed25519 verification BEFORE any field access.
- **Constant-time hash comparison:** SHA-256 verification uses constant-time comparison, not `==`.

## Compose and UI Patterns

- **Theme-first:** All colors, typography, spacing from `LocalForgeColors`, `ForgeTheme.colors`. Zero hardcoded `Color()` values.
- **State management:** `ViewModel` + `StateFlow<SealedUiState>` + `collectAsStateWithLifecycle`.
- **Navigation:** Compose Navigation with type-safe routes. One `NavHost`.
- **Data classes for Compose params:** Mark with `@Stable` or `@Immutable`.
- **Accessibility:** Every `Icon` has a semantic `contentDescription` (or `null` for decorative). Touch targets ≥ 48dp.
- **Dark mode default.** Light mode fully implemented as secondary.

## Rust Patterns

- **Streaming, never buffering.** 256 KB chunks. No partition fully loaded into memory.
- **Rich error types.** Every error variant carries diagnostic context.
- **All public types derive `Debug`, `Display`, and `std::error::Error`.**
- **No `unsafe` outside `forge-jni`.**
- **Tests are in-crate** as `#[cfg(test)]` modules. Descriptive names.

## Error Handling Contract

Every error the user sees must be:
- **Specific:** Name the failure class. "Insufficient storage: need 340 MB, 210 MB available."
- **Actionable:** Include a next step. "Choose a different destination" or "Free 130 MB and retry."
- **Scoped:** A failure in one partition does not fail the entire job.
- **Instrumented:** Every failure class emits a structured event.

Use exhaustive `when` statements that fail to compile if a new error subclass is added.

## What This App Does NOT Do (v1)

1. Flash firmware to a device
2. Unlock bootloaders or provide root guidance
3. Boot image patching or Magisk integration
4. OTA authoring, repacking, or re-signing
5. Download OEM firmware from the internet
6. Claim universal support for proprietary/encrypted formats
7. iOS anything
8. Desktop companion
9. Cloud extraction

## Commit Discipline

- Conventional format: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`
- Comments explain WHY, not WHAT
- Every new screen gets an accessibility comment block
- ADRs for non-obvious decisions go in `docs/decisions/`
- No TODO without an issue reference or `// TODO(P0X):` prompt reference
