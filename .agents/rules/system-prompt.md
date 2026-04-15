---
trigger: always_on
---

# Forge OTA Lab — Agent Operating Contract

You are building Forge OTA Lab: an Android-first mobile extraction workbench for Android OTA packages. The app imports OTA archives, classifies them truthfully by support tier, extracts verifiable partition images, reconstructs supported incremental outputs from validated bases, and exports artifacts — all local-first, on-device.

This is a production application shipping to real users via Google Play. Not a prototype, not a learning exercise, not a minimum viable product. Every file you write ships.

---

## Source of Truth

`prd/PRD-ForgeOTALab.md` is the finalized production PRD. When this document and any other source conflict, the PRD wins. Read the relevant PRD section before writing code for any feature — the PRD specifies failure behaviors, state transitions, instrumentation events, and non-functional targets that are easy to miss.

Companion design files:
- `prd/tokens/primitives.json` — raw OKLCH color scales
- `prd/tokens/semantic.json` — light/dark semantic color mappings
- `prd/tokens/components.json` — component-level design token recipes
- `prd/build/css/variables.css` — reference CSS output for the color system

---

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
```

This layout is non-negotiable. Do not flatten it, merge modules, or restructure without explicit approval.

---

## Tech Stack — Fixed Decisions

| Layer | Technology | Constraint |
|:------|:-----------|:-----------|
| Language | Kotlin 2.0+ | No Java source files |
| UI | Jetpack Compose + Material 3 | No XML layouts. No View system. |
| Theme | Custom OKLCH-native token system | Dynamic color (Material You) OFF. Forge identity is the identity. |
| DI | Hilt | No manual DI, no Koin, no Dagger without Hilt |
| Persistence | Room (KSP) | No KAPT. Additive migrations only during beta. |
| Background work | WorkManager | No raw Services for long-running jobs |
| Storage | SAF (Storage Access Framework) | No broad filesystem access by default |
| Native core | Rust (edition 2024) via cargo-ndk | No C/C++ in core logic |
| JNI bridge | `jni` crate + JSON serialization | Results cross JNI as serialized JSON, not raw JNI types |
| Protobuf | prost | Not protobuf-java, not wire |
| Async | Kotlin Coroutines + Flow | No RxJava. No LiveData in new code. |
| Targets | API 35 (target), API 30 (min) | |

---

## Quality Bar

Code that ships to Forge OTA Lab users meets this standard:

**Kotlin/Compose:** A principal Android engineer at Square would review this code and approve it without requesting structural changes. ViewModels use `StateFlow` with sealed UI state classes. Repositories abstract data sources behind domain interfaces. Compose screens use the theme system — no hardcoded colors, dimensions, or strings. Error states are specific and actionable, never generic "Something went wrong."

**Rust:** A systems engineer at Cloudflare would review this parser and find it safe against adversarial input. All arithmetic on untrusted values uses checked operations. All decompression is bounded. All JNI exports catch panics. No `unsafe` outside the JNI boundary. Errors carry context — `FormatError::UnsupportedVersion { found: 2, expected: 1 }`, not `"unsupported version"`.

**Integration:** The JNI boundary is the highest-risk surface. Results are serialized as JSON — less efficient than direct JNI types, but verifiable and debuggable. Every JNI function wraps its body in `std::panic::catch_unwind`. No Rust panic ever reaches the Android app shell.

---

## Non-Negotiable Rules

These come directly from the PRD. Violating any of them is a ship-blocking defect.

1. **UI support tier comes only from native core output.** The Kotlin UI never decides whether a package is Supported, Experimental, or Forensic — that classification comes exclusively from the Rust core's analysis result.

2. **No extraction starts before storage and permission validation.** Preflight checks (storage budget, SAF permission, adapter readiness) must pass before any extraction job is created.

3. **No success state before verification completes.** No green badge, no checkmark, no "Extraction complete" message until SHA-256 verification passes. A passing extraction with a failing verification is NOT success.

4. **Unknown formats never map to success visuals.** An unrecognized file renders in Forensic mode with informational treatment. Never a green CTA. Never "Ready to extract."

5. **Incremental outputs always carry `derivation_type = reconstructed` or `raw_unverified`.** Never label a reconstructed partition as a direct extract.

6. **Partial success is first-class.** If 5 of 7 partitions extract and verify but 2 fail, the job is partial success — not failure. Verified outputs remain available.

7. **Adapter expansion does not change the public support promise** unless the manifest tier changes and the corpus gate passes.

8. **Cancellation preserves verified outputs.** User cancellation cleans temp files and in-progress partitions. Completed, verified partitions are never deleted by cancellation.

9. **No package contents, filenames, or raw paths uploaded automatically.** Diagnostics are user-triggered, stripped of PII by design.

---

## Error Handling Contract

Every error the user sees must be:
- **Specific:** Name the failure class. "Insufficient storage: need 340 MB, 210 MB available" — not "Storage error."
- **Actionable:** Include a next step. "Choose a different destination" or "Free 130 MB and retry."
- **Scoped:** A failure in one partition does not fail the entire job unless the adapter requires it.
- **Instrumented:** Every failure class emits a structured event per the PRD's Required Events list.

Map every `AnalysisError` and `ExtractionError` variant to a user-facing string resource. Use a `when` statement that fails to compile if a new error subclass is added without a message — exhaustive by construction, not by discipline.

---

## Security Boundaries

From the PRD's Threat Model — these are not optional hardening, they are structural requirements:

- **Bounded manifest parse:** ≤ 64 MB allocation. 10-second timeout.
- **Integer overflow:** All extent calculations use `checked_mul` / `checked_add`.
- **Decompression bomb:** Abort if `bytes_written > declared_size × 1.01`. Checked per chunk, not at end.
- **Path traversal:** Canonicalize output paths. Reject if resolved path escapes destination directory.
- **ZIP bomb:** 1,000-entry limit on central directory scan. No recursive extraction.
- **JNI panic isolation:** `catch_unwind` on every exported function. Return serialized error, never propagate panic.
- **Manifest signature:** Ed25519 verification BEFORE any field access. Public key pinned in binary.
- **Constant-time hash comparison:** SHA-256 verification uses constant-time comparison, not `==`.

---

## Compose and UI Patterns

- **Theme-first:** All colors, typography, spacing, and component treatments from the Forge theme system. `LocalForgeColors`, `LocalForgeComponents`. Zero hardcoded `Color()` values.
- **State management:** `ViewModel` + `StateFlow<SealedUiState>` + `collectAsStateWithLifecycle`. No `LiveData`. No mutable state in Composables.
- **Navigation:** Compose Navigation with type-safe routes. One `NavHost`. Deep links for share/open-with intents.
- **Data classes for Compose params:** Mark with `@Stable` or `@Immutable` to prevent unnecessary recomposition.
- **Accessibility by default:** Every `Icon` has a semantic `contentDescription` (or `null` for decorative). Touch targets ≥ 48dp. Status icons describe meaning ("Verified"), not appearance ("green circle"). `Role.Heading` on screen titles.
- **Dark mode default.** Light mode fully implemented as a secondary option.

---

## Rust Patterns

- **Streaming, never buffering.** Extraction reads and writes in 256 KB chunks. No partition is fully loaded into memory.
- **Error types are rich.** Every error variant carries the context needed to diagnose from the Kotlin side: byte offsets, expected vs actual values, operation types.
- **All public types derive `Debug`, `Display`, and `std::error::Error`.**
- **No `unsafe` outside `forge-jni`.** The JNI boundary is the only place `unsafe` is tolerated.
- **Tests are in-crate** as `#[cfg(test)]` modules. Test names describe the behavior: `test_decompression_bomb_fires_at_101_percent`, not `test_decompression`.

---

## Testing Expectations

- **Unit tests:** Every public function with non-trivial logic. Descriptive names. No unnecessary mocking.
- **Integration tests:** End-to-end flows against synthetic corpus fixtures in `test-corpus/`.
- **UI tests:** Espresso + Compose Test with `AccessibilityChecks.enable()`.
- **Fault injection:** Corrupted archives, disk exhaustion, SAF revocation, JNI panic containment, process death recovery.
- **The PRD's Failure Taxonomy (rows 1–13):** Every entry has at least one test.

---

## What This App Does NOT Do

These are explicit non-goals. Do not implement, suggest, or plan for them in v1:

1. Flash firmware to a device
2. Unlock bootloaders or provide root guidance
3. Patch boot images or integrate Magisk
4. Author, repack, or re-sign OTA packages
5. Download OEM firmware from the internet
6. Claim universal support for proprietary/encrypted formats
7. iOS anything
8. Desktop companion
9. Cloud extraction

If a user request implies any of these, confirm it's intentional before proceeding.

---

## Commit and Documentation Discipline

- Commit messages: conventional format (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`)
- Comments: explain WHY, not WHAT. The code explains what.
- Every new screen gets an accessibility comment block above it listing content descriptions, focus order notes, and heading semantics.
- ADRs for non-obvious architectural decisions go in `docs/decisions/`.
- No TODO without an issue reference or a `// TODO(P0X):` prompt reference.
