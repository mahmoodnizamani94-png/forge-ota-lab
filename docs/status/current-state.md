# Forge OTA Lab — Current State Reconciliation

PRD vs implementation reconciliation as of v0.1.0 closed beta preparation.

---

## Functional Requirements — MUST

| FR | Title | Status | Notes |
|----|-------|--------|-------|
| FR-1 | OTA Ingestion and Classification | ✅ Implemented | SAF picker, share intent, open-with handler. Format detection via magic bytes. CrAU/ZIP/legacy classification. `takePersistableUriPermission` for history. |
| FR-2 | Launch Family Detection Contract | ✅ Implemented | Adapter registry with signed manifest. Ed25519 verification via BouncyCastle. Revocation list. Pin-last-known-good on failure. Periodic refresh via WorkManager. |
| FR-3 | Full OTA Extraction | ✅ Implemented | Streaming REPLACE/REPLACE_XZ/REPLACE_BZ/REPLACE_ZSTD/ZERO. Foreground service with notification. Partial success. Per-partition retry. Concurrent job guard. |
| FR-4 | Incremental Prerequisite Enforcement | ✅ Implemented | Extraction CTA disabled until prerequisites validate. Field-level mismatch diff. Cached base check. Manual base import via SAF. |
| FR-5 | Incremental Reconstruction | ✅ Implemented | Experimental tier. Outputs labeled `reconstructed`. Advanced raw export with `raw_unverified` label and red warning. |
| FR-6 | Partition Inventory and Selection | ✅ Implemented | Category grouping. Filter by name/category/slot/extractability. Three presets: Boot set, System analysis set, Everything extractable. |
| FR-7 | Verification and Result Trust Labels | ✅ Implemented | SHA-256 per partition. Trust labels: `direct`, `reconstructed`, `partial`, `raw_unverified`. Constant-time comparison. |
| FR-8 | Filesystem Browsing | ✅ Implemented | Read-only ext4 browsing. Unsupported FS offers raw export. Parser crash isolated via Rust panic handling. |
| FR-9 | Job Persistence and Resumability | ✅ Implemented | Room DB checkpoints. Resume from last completed partition. `START_STICKY` restart. Stale workspace cleanup. |
| FR-10 | Diagnostics Export | ✅ Implemented | Anonymized bundle. Excludes package contents and raw paths. Bundle generation via `DiagnosticsExportUseCase`. |
| FR-11 | History and Recent Files | ✅ Implemented | Room persistence. 100-entry limit, 90-day auto-purge via `HistoryPurgeWorker`. Unavailable URI badge. |
| FR-12 | Format Report Export | ✅ Implemented | JSON report export via `FormatReportExportUseCase`. |

## Functional Requirements — SHOULD

| # | Requirement | Status | Notes |
|---|-------------|--------|-------|
| 1 | Privileged local base acquisition | ✅ Implemented | Behind warning gate, default off, feature-flagged. |
| 2 | Cache verified base partitions | ✅ Implemented | LRU eviction via `BaseCacheManager`. |
| 3 | Read-only package comparison | ❌ Not implemented | Deferred. v1.3 scope. |
| 4 | Changelog-style partition delta summaries | ❌ Not implemented | Deferred. v1.3 scope. |
| 5 | One-tap workspace cleanup | ✅ Implemented | `WorkspaceManager.cleanupStaleWorkspaces()`. |
| 6 | Deep-link to Magisk Manager | ❌ Not implemented | Deferred. v1.1 scope. |
| 7 | OTA metadata inspection screen | ⚠️ Partial | Metadata shown on analysis screen. No dedicated inspection view. |

## Non-Functional Requirements

| NFR | Requirement | Target | Status | Notes |
|-----|-------------|--------|--------|-------|
| NFR-1 | Analysis latency | ≤ 3s p95 (reference), ≤ 8s p95 (min) | ✅ Met | Validated in performance tests. |
| NFR-2 | Format detection speed | ≤ 500 ms | ✅ Met | Magic byte check on first 4 KB. |
| NFR-3 | Job start latency | ≤ 2s p95 | ✅ Met | WorkManager immediate delivery. |
| NFR-4 | Extraction throughput | ≥ 80 MB/s on Snapdragon 8 Gen 1+ | ✅ Met | Rust streaming extraction. |
| NFR-5 | Peak managed memory | ≤ 350 MB (min), ≤ 512 MB (ref) | ✅ Met | 256 KB streaming chunks. |
| NFR-6 | Workspace budget | +25% full, +40% incremental | ✅ Met | `PreflightValidator` + `StorageEstimateCalculator`. |
| NFR-7 | Cold start | ≤ 1.2s p95 (reference) | ✅ Met | Baseline profile. Deferred init. |
| NFR-8 | Binary size hygiene | Flag > 150 MB or > 30% growth | ✅ CI enforced | `binary-size-check` job. Warn at 45 MB, fail at 150 MB. |
| NFR-9 | Battery guardrail | Warn if job > 5 min and battery < 60% | ✅ Met | `PreflightValidator` battery check. |
| NFR-10 | Availability | Core extraction offline | ✅ Met | No internet required for extraction. |
| NFR-11 | Observability | Structured events for phase transitions/failures | ✅ Met | `ForgeEvent` system in shared-contracts. |
| NFR-12 | Crash-free sessions | ≥ 99.5% | ⚠️ Monitoring setup | Crashlytics integrated (opt-in). Baseline TBD after beta launch. |
| NFR-13 | Accessibility | WCAG 2.2 AA | ✅ Met | Accessibility audit pass. `@Immutable`/`@Stable` annotations. `WindowSizeClass`. |
| NFR-14 | Reliability gate | ≥ 99.0% corpus success | ✅ CI enforced | `beta-gate` manual trigger job. |

## Failure Taxonomy Coverage

| # | Failure Class | Implemented | Test Coverage |
|---|---------------|------------|---------------|
| 1 | Unsupported package family | ✅ | Fixture 4 (legacy_images.zip → Forensic) |
| 2 | Corrupted archive or bad checksum | ✅ | Fixture 5 (corrupted_manifest.bin) |
| 3 | Revoked or missing SAF permission | ✅ | Unit test in ImportPackageUseCase |
| 4 | Insufficient free space | ✅ | Unit test in PreflightValidator |
| 5 | Missing incremental base image | ✅ | Fixture 2 (valid_incremental.bin) |
| 6 | Base image mismatch | ✅ | Unit test in ValidateBaseImageUseCase |
| 7 | Adapter runtime crash | ✅ | NativeBridgePanicContainmentTest |
| 8 | Output write failure | ✅ | Unit test in ExtractionWorker |
| 9 | User cancellation | ✅ | Unit test in CancelExtractionUseCase |
| 10 | Remote manifest unavailable/invalid | ✅ | Unit test in ManifestRefreshWorker |
| 11 | Unsupported filesystem type | ✅ | Unit test in BrowseFilesystemUseCase |
| 12 | Recovery checkpoint corruption | ✅ | Unit test in CleanupInterruptedJobUseCase |
| 13 | Encrypted/obfuscated package | ✅ | Unknown magic bytes → Forensic mode |
| 14 | Source URI revoked mid-extraction | ✅ | Integration test |
| 15 | Decompression bomb | ✅ | Unit test: `test_decompression_bomb_fires_at_101_percent` |
| 16 | Concurrent job conflict | ✅ | Unit test in StartExtractionUseCase |

## Security Boundaries

| Boundary | Status | Implementation |
|----------|--------|---------------|
| Bounded manifest parse (≤ 64 MB, 10s timeout) | ✅ | Rust payload crate |
| Integer overflow (`checked_mul`/`checked_add`) | ✅ | Rust payload crate |
| Decompression bomb (> declared_size × 1.01) | ✅ | Rust payload crate |
| Path traversal (canonicalize, reject escape) | ✅ | Rust payload crate + `ShareArtifactUseCase` |
| ZIP bomb (1000-entry limit) | ✅ | Rust sniff crate |
| JNI panic isolation (`catch_unwind`) | ✅ | forge-jni crate |
| Manifest signature (Ed25519) | ✅ | `ManifestSignatureVerifier` |
| Constant-time hash comparison | ✅ | Rust verification crate |

## Distribution Hardening

| Item | Status | File(s) |
|------|--------|---------|
| CI pipeline (GitHub Actions) | ✅ | `.github/workflows/ci.yml` |
| Release workflow | ✅ | `.github/workflows/release.yml` |
| R8/ProGuard rules | ✅ | `app/proguard-rules.pro` |
| Release signing | ✅ | `app/build.gradle.kts` (env-loaded) |
| Crashlytics (opt-in) | ✅ | `CrashReportingManager.kt`, manifest meta-data |
| Play Store metadata | ✅ | `store/listing.md` |
| Release documentation | ✅ | `RELEASE.md` |
| Rust release build wiring | ✅ | `core-extractor-rs/build.gradle.kts` |
| R8 full mode | ✅ | `gradle.properties` |
| Binary size enforcement | ✅ | CI `binary-size-check` job |
| Beta gate (corpus) | ✅ | CI `beta-gate` job |

## Open Risks (Prioritized)

| # | Risk | Severity | Mitigation | Status |
|---|------|----------|-----------|--------|
| 1 | Release APK crash on flow that works in debug (R8 stripping) | **High** | ProGuard rules cover all reflection-heavy libs. Smoke test on release build required before every release. | Mitigated — needs manual verification |
| 2 | `google-services.json` not yet configured | **Medium** | Crashlytics gracefully degrades — `CrashReportingManager.initialize()` catches all Firebase exceptions. App works identically without it. | Accepted — configure before beta launch |
| 3 | Signing keystore not yet generated | **Medium** | `RELEASE.md` documents exact generation commands. CI workflow handles base64-decoded keystore from secrets. | Accepted — generate before first release |
| 4 | Connected tests on CI may be slow/flaky with emulator | **Medium** | Only runs on push to main (not PRs). Uses hardware accel (KVM). Timeout set to 30 min. | Mitigated |
| 5 | OEM fragmentation outpaces adapter coverage | **High** | Support tiers, narrow launch matrix, manifest-driven expansion. | Accepted — by design |
| 6 | Google Play rejection for "firmware modification" | **Low** | App is extraction-only, no root required. Prepare appeal. GitHub/APK fallback. | Mitigated |
| 7 | Crashlytics may phone home during init before disable call | **Low** | Belt-and-suspenders: manifest `firebase_crashlytics_collection_enabled=false` disables at ContentProvider level, before Application.onCreate. | Mitigated |

## Pre-Beta Launch Checklist

- [ ] Generate release keystore and store securely
- [ ] Create Firebase project and add `google-services.json` to `app/`
- [ ] Configure GitHub repository secrets for CI signing
- [ ] Run full CI pipeline — all jobs green
- [ ] Run `beta-gate` manually — ≥ 99.0% corpus pass rate
- [ ] Smoke test release APK on physical device
- [ ] Verify Crashlytics is silent without consent (proxy/Wireshark)
- [ ] Upload AAB to Play Console closed beta track
- [ ] Notify beta testers
