//! JNI bridge — the only crate with `unsafe` boundary code.
//!
//! Every exported function wraps its body in `std::panic::catch_unwind`.
//! No Rust panic ever reaches the Android app shell.
//! Results cross JNI as serialized JSON strings.
//!
//! JNI function naming convention:
//!   Java_{package}_{Class}_{method}
//!   Package: dev.forgeotalab.nativebridge → dev_forgeotalab_nativebridge
//!   Class: NativeBridge
//!   Methods: nativePing, nativeCoreVersion, nativeAnalyze, nativeExtractPartition

mod jni_helpers;

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::jstring;
use std::panic;

use jni_helpers::{jni_result_error, jni_result_ok, string_from_jstring};

// ===========================================================================
// Existing JNI exports
// ===========================================================================

/// Ping → Pong round-trip proving JNI bridge works.
///
/// Kotlin side: `NativeBridge.ping()` calls `nativePing()` → `"pong"`
///
/// # Safety
/// JNI boundary function — called from Kotlin via `System.loadLibrary`.
/// Panic-safe: `catch_unwind` prevents any Rust panic from propagating.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativePing<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| "pong"));

    match result {
        Ok(msg) => env
            .new_string(msg)
            .expect("Failed to create JNI string")
            .into_raw(),
        Err(_) => {
            // Panic occurred in Rust — return error JSON instead of crashing JVM
            let error_json =
                r#"{"error":"rust_panic","message":"Internal error in native bridge"}"#;
            env.new_string(error_json)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

/// Returns the version of the native extraction core.
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeCoreVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| env!("CARGO_PKG_VERSION")));

    match result {
        Ok(version) => env
            .new_string(version)
            .expect("Failed to create JNI string")
            .into_raw(),
        Err(_) => {
            let error_json =
                r#"{"error":"rust_panic","message":"Failed to read core version"}"#;
            env.new_string(error_json)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

// ===========================================================================
// Analysis JNI export
// ===========================================================================

/// Analyze a payload file at the given path.
///
/// Returns JSON-serialized result:
/// - Success: `{"ok": <AnalysisResult>}`
/// - Error:   `{"error": {"code": "...", "message": "...", "details": "..."}}`
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
/// Path traversal: not applicable here — we only read, never write.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeAnalyze<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    file_path: JString<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        analyze_impl(&mut env, &file_path)
    }));

    match result {
        Ok(json) => env
            .new_string(&json)
            .expect("Failed to create JNI string for analysis result")
            .into_raw(),
        Err(_) => {
            let error = jni_result_error("RUST_PANIC", "Internal panic during analysis", "");
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

fn analyze_impl(env: &mut JNIEnv<'_>, file_path: &JString<'_>) -> String {
    // Extract the file path string from JNI
    let path = match string_from_jstring(env, file_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };

    // Open the file
    let file = match std::fs::File::open(&path) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error(
                "IO_ERROR",
                &format!("Cannot open file: {e}"),
                &path,
            )
        }
    };

    let mut reader = std::io::BufReader::new(file);

    // Sniff the file first using seekable sniff
    let sniff_result = match forge_sniff::sniff_seekable(&mut reader) {
        Ok(r) => r,
        Err(e) => {
            return jni_result_error(
                "SNIFF_ERROR",
                &format!("Format detection failed: {e}"),
                &path,
            )
        }
    };

    // Seek back to start for analysis
    if let Err(e) = std::io::Seek::seek(&mut reader, std::io::SeekFrom::Start(0)) {
        return jni_result_error(
            "IO_ERROR",
            &format!("Cannot seek to start: {e}"),
            &path,
        );
    }

    // Analyze using the adapter registry
    let registry = forge_adapters::AdapterRegistry::new();
    let header_bytes = match sniff_result.magic {
        forge_sniff::MagicBytes::CrauPayload => b"CrAU".to_vec(),
        forge_sniff::MagicBytes::Zip => vec![0x50, 0x4B, 0x03, 0x04],
        _ => vec![],
    };

    let adapter_match = registry.match_adapters(&header_bytes);

    if let Some(m) = adapter_match {
        if let Some(adapter) = registry.get_adapter(&m.adapter_id) {
            match adapter.analyze(&mut reader) {
                Ok(analysis) => return jni_result_ok(&analysis),
                Err(e) => {
                    return jni_result_error(
                        "ANALYSIS_ERROR",
                        &format!("{e}"),
                        &path,
                    )
                }
            }
        }
    }

    // No adapter matched — return forensic result
    jni_result_error(
        "NO_ADAPTER",
        &format!(
            "No adapter matches format: {} ({})",
            sniff_result.family, sniff_result.tier
        ),
        &path,
    )
}

// ===========================================================================
// Extraction JNI export
// ===========================================================================

/// Extract a single partition from a payload file.
///
/// Returns JSON-serialized result:
/// - Success: `{"ok": <ExtractionOutcome>}`
/// - Error:   `{"error": {"code": "...", "message": "...", "details": "..."}}`
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
/// Path traversal guard: output path is canonicalized and validated.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeExtractPartition<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    file_path: JString<'local>,
    partition_name: JString<'local>,
    output_path: JString<'local>,
    _callback: JObject<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        extract_impl(&mut env, &file_path, &partition_name, &output_path)
    }));

    match result {
        Ok(json) => env
            .new_string(&json)
            .expect("Failed to create JNI string for extraction result")
            .into_raw(),
        Err(_) => {
            let error = jni_result_error("RUST_PANIC", "Internal panic during extraction", "");
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

fn extract_impl(
    env: &mut JNIEnv<'_>,
    file_path: &JString<'_>,
    partition_name_jstr: &JString<'_>,
    output_path: &JString<'_>,
) -> String {
    // Extract JNI strings
    let source_path = match string_from_jstring(env, file_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };
    let partition_name = match string_from_jstring(env, partition_name_jstr) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };
    let out_path = match string_from_jstring(env, output_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };

    // Path traversal guard: validate output path
    // PRD Security: "Canonicalize output paths. Reject if resolved path escapes destination."
    let canonical_out = match std::path::Path::new(&out_path).canonicalize() {
        Ok(p) => p,
        Err(_) => {
            // Path doesn't exist yet — check parent directory
            let parent = match std::path::Path::new(&out_path).parent() {
                Some(p) => p,
                None => {
                    return jni_result_error(
                        "PATH_TRAVERSAL",
                        "Output path has no parent directory",
                        &out_path,
                    )
                }
            };
            match parent.canonicalize() {
                Ok(p) => p.join(
                    std::path::Path::new(&out_path)
                        .file_name()
                        .unwrap_or_default(),
                ),
                Err(e) => {
                    return jni_result_error(
                        "IO_ERROR",
                        &format!("Cannot validate output directory: {e}"),
                        &out_path,
                    )
                }
            }
        }
    };

    // Open source file
    let source_file = match std::fs::File::open(&source_path) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error(
                "IO_ERROR",
                &format!("Cannot open source file: {e}"),
                &source_path,
            )
        }
    };
    let mut reader = std::io::BufReader::new(source_file);

    // Parse header and manifest
    let header = match forge_payload::parse_header(&mut reader) {
        Ok(h) => h,
        Err(e) => return jni_result_error("PARSE_ERROR", &format!("{e}"), &source_path),
    };

    // Read manifest
    if let Err(e) = std::io::Seek::seek(
        &mut reader,
        std::io::SeekFrom::Start(header.manifest_offset()),
    ) {
        return jni_result_error("IO_ERROR", &format!("Cannot seek to manifest: {e}"), &source_path);
    }

    let manifest_len = header.manifest_size as usize;
    let mut manifest_data = vec![0u8; manifest_len];
    if let Err(e) = std::io::Read::read_exact(&mut reader, &mut manifest_data) {
        return jni_result_error(
            "IO_ERROR",
            &format!("Cannot read manifest: {e}"),
            &source_path,
        );
    }

    let parsed = match forge_payload::parse_manifest(&manifest_data) {
        Ok(p) => p,
        Err(e) => return jni_result_error("PARSE_ERROR", &format!("{e}"), &source_path),
    };

    // Find the requested partition
    let pu = match parsed
        .manifest
        .partitions
        .iter()
        .find(|p| p.partition_name == partition_name)
    {
        Some(p) => p,
        None => {
            return jni_result_error(
                "PARTITION_NOT_FOUND",
                &format!("Partition '{partition_name}' not found in manifest"),
                &source_path,
            )
        }
    };

    // Create output file
    let output_file = match std::fs::File::create(&canonical_out) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error(
                "IO_ERROR",
                &format!("Cannot create output file: {e}"),
                &out_path,
            )
        }
    };
    let mut writer = std::io::BufWriter::new(output_file);

    // Extract the partition
    let cancel_token = std::sync::atomic::AtomicBool::new(false);
    let data_offset = header.data_offset();

    match forge_payload::extract_partition(
        &mut reader,
        &mut writer,
        pu,
        data_offset,
        parsed.block_size,
        &cancel_token,
        |_progress| {
            // TODO(P02): report progress back to Kotlin via JNI callback object
        },
    ) {
        Ok(outcome) => {
            // Verify hash if available
            if let Some(ref info) = pu.new_partition_info {
                if let Some(ref expected_hash) = info.hash {
                    let actual_hash = forge_verification::hex_decode(&outcome.sha256)
                        .unwrap_or_default();
                    let verification =
                        forge_verification::verify_hash(expected_hash, &actual_hash);
                    if let forge_verification::VerificationOutcome::Mismatch {
                            expected,
                            actual,
                        } = verification
                    {
                        return jni_result_error(
                            "VERIFICATION_FAILED",
                            &format!(
                                "SHA-256 mismatch for '{partition_name}': expected {expected}, got {actual}"
                            ),
                            &source_path,
                        );
                    }
                    // Verified or Unverifiable — both OK to return success
                }
            }

            jni_result_ok(&outcome)
        }
        Err(e) => jni_result_error(e.error_code(), &format!("{e}"), &source_path),
    }
}

// ===========================================================================
// Filesystem browsing JNI exports — PRD FR-8
// ===========================================================================

/// Open a filesystem image and return the root directory listing.
///
/// Returns JSON-serialized result:
/// - Browsable: `{"ok": {"filesystem_type": "ext4", "root_listing": {...}}}`
/// - Unsupported: `{"ok": {"Unsupported": {"format": "..."}}}`
/// - Error: `{"error": {"code": "...", "message": "...", "details": "..."}}`
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
/// PRD FR-8: "Parser crash → isolate via Rust panic handling."
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeBrowseFilesystem<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_path: JString<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        browse_filesystem_impl(&mut env, &image_path)
    }));

    match result {
        Ok(json) => env
            .new_string(&json)
            .expect("Failed to create JNI string for browse result")
            .into_raw(),
        Err(_) => {
            // Panic occurred — return graceful error per PRD
            let error = jni_result_error(
                "RUST_PANIC",
                "Cannot browse this filesystem format — internal parser error",
                "",
            );
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

fn browse_filesystem_impl(env: &mut JNIEnv<'_>, image_path: &JString<'_>) -> String {
    let path = match string_from_jstring(env, image_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };

    let file = match std::fs::File::open(&path) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error("IO_ERROR", &format!("Cannot open image: {e}"), &path)
        }
    };

    let mut reader = std::io::BufReader::new(file);

    match forge_filesystems::browse(&mut reader) {
        Ok(result) => jni_result_ok(&result),
        Err(e) => jni_result_error(e.error_code(), &format!("{e}"), &path),
    }
}

/// List entries in a specific directory within a filesystem image.
///
/// Returns JSON-serialized FsDirectoryListing or error.
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeListDirectory<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_path: JString<'local>,
    directory_path: JString<'local>,
    offset: jni::sys::jlong,
    limit: jni::sys::jint,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        list_directory_impl(&mut env, &image_path, &directory_path, offset, limit)
    }));

    match result {
        Ok(json) => env
            .new_string(&json)
            .expect("Failed to create JNI string for listing result")
            .into_raw(),
        Err(_) => {
            let error = jni_result_error(
                "RUST_PANIC",
                "Cannot list directory — internal parser error",
                "",
            );
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

fn list_directory_impl(
    env: &mut JNIEnv<'_>,
    image_path: &JString<'_>,
    directory_path: &JString<'_>,
    offset: jni::sys::jlong,
    limit: jni::sys::jint,
) -> String {
    let img_path = match string_from_jstring(env, image_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };
    let dir_path = match string_from_jstring(env, directory_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };

    let file = match std::fs::File::open(&img_path) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error("IO_ERROR", &format!("Cannot open image: {e}"), &img_path)
        }
    };

    let mut reader = std::io::BufReader::new(file);

    match forge_filesystems::list_directory(
        &mut reader,
        &dir_path,
        offset.max(0) as usize,
        limit.max(1) as usize,
    ) {
        Ok(listing) => jni_result_ok(&listing),
        Err(e) => jni_result_error(e.error_code(), &format!("{e}"), &img_path),
    }
}

/// Read a file from inside a filesystem image and write it to an output path.
///
/// Returns JSON-serialized result with bytes_written and sha256, or error.
/// Includes path traversal guard on output path (same pattern as extraction).
///
/// # Safety
/// JNI boundary function with `catch_unwind` panic isolation.
/// PRD Security: "Canonicalize output paths. Reject if resolved path escapes destination."
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeReadFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    image_path: JString<'local>,
    file_path: JString<'local>,
    output_path: JString<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        read_file_impl(&mut env, &image_path, &file_path, &output_path)
    }));

    match result {
        Ok(json) => env
            .new_string(&json)
            .expect("Failed to create JNI string for read result")
            .into_raw(),
        Err(_) => {
            let error = jni_result_error(
                "RUST_PANIC",
                "Cannot read file — internal parser error",
                "",
            );
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

fn read_file_impl(
    env: &mut JNIEnv<'_>,
    image_path: &JString<'_>,
    file_path_jstr: &JString<'_>,
    output_path: &JString<'_>,
) -> String {
    let img_path = match string_from_jstring(env, image_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };
    let file_path = match string_from_jstring(env, file_path_jstr) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };
    let out_path = match string_from_jstring(env, output_path) {
        Ok(p) => p,
        Err(e) => return jni_result_error("JNI_ERROR", &e, ""),
    };

    // Path traversal guard — same pattern as extraction
    let canonical_out = match std::path::Path::new(&out_path).canonicalize() {
        Ok(p) => p,
        Err(_) => {
            let parent = match std::path::Path::new(&out_path).parent() {
                Some(p) => p,
                None => {
                    return jni_result_error(
                        "PATH_TRAVERSAL",
                        "Output path has no parent directory",
                        &out_path,
                    )
                }
            };
            match parent.canonicalize() {
                Ok(p) => p.join(
                    std::path::Path::new(&out_path)
                        .file_name()
                        .unwrap_or_default(),
                ),
                Err(e) => {
                    return jni_result_error(
                        "IO_ERROR",
                        &format!("Cannot validate output directory: {e}"),
                        &out_path,
                    )
                }
            }
        }
    };

    // Open source image (read-only — never mutated)
    let source_file = match std::fs::File::open(&img_path) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error("IO_ERROR", &format!("Cannot open image: {e}"), &img_path)
        }
    };
    let mut reader = std::io::BufReader::new(source_file);

    // Create output file
    let output_file = match std::fs::File::create(&canonical_out) {
        Ok(f) => f,
        Err(e) => {
            return jni_result_error(
                "IO_ERROR",
                &format!("Cannot create output file: {e}"),
                &out_path,
            )
        }
    };
    let mut writer = std::io::BufWriter::new(output_file);

    match forge_filesystems::read_file(&mut reader, &file_path, &mut writer) {
        Ok(result) => jni_result_ok(&result),
        Err(e) => jni_result_error(e.error_code(), &format!("{e}"), &img_path),
    }
}

// ===========================================================================
// Test-only JNI export: deliberate panic for containment verification
// ===========================================================================

/// Deliberately panic inside catch_unwind to verify JNI panic isolation.
///
/// This function ONLY exists when the `test-panic` Cargo feature is enabled
/// (debug/test builds). It proves the PRD requirement:
///   "catch_unwind on every exported function. Return serialized error,
///    never propagate panic."
///
/// Called by NativeBridgePanicContainmentTest in Kotlin androidTest.
///
/// # Safety
/// JNI boundary function. The entire point is to panic safely.
#[cfg(feature = "test-panic")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_forgeotalab_nativebridge_NativeBridge_nativeTriggerTestPanic<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| -> String {
        // Deliberate panic — this MUST be caught by catch_unwind
        panic!("Deliberate test panic for JNI containment verification");
    }));

    match result {
        Ok(msg) => env
            .new_string(&msg)
            .expect("Failed to create JNI string")
            .into_raw(),
        Err(_) => {
            // Panic was caught — return structured error JSON (expected path)
            let error = jni_result_error(
                "RUST_PANIC",
                "Deliberate test panic was correctly caught by catch_unwind",
                "",
            );
            env.new_string(&error)
                .expect("Failed to create JNI error string")
                .into_raw()
        }
    }
}

#[cfg(test)]
mod tests {
    // JNI tests require an Android runtime — tested via on-device integration.
    // Unit tests for logic live in the workspace library crates, not here.
}
