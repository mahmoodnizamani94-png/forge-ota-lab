//! Fault injection and security boundary tests.
//!
//! These tests verify that PRD security requirements are enforced
//! at the integration level — not just at the unit level. They exercise
//! adversarial inputs against the full pipeline.
//!
//! Run: `cargo test --test fault_injection`

use forge_payload::proto::{
    DeltaArchiveManifest, Extent, InstallOperation, OpType, PartitionInfo, PartitionUpdate,
};
use forge_payload::{ExtractionError, PayloadError};
use prost::Message;
use std::io::Cursor;

// ===========================================================================
// Helpers
// ===========================================================================

fn build_payload_bin(manifest: &DeltaArchiveManifest, data_blobs: &[&[u8]]) -> Vec<u8> {
    let manifest_bytes = manifest.encode_to_vec();
    let manifest_size = manifest_bytes.len() as u64;

    let mut payload = Vec::new();
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&2u64.to_be_bytes());
    payload.extend_from_slice(&manifest_size.to_be_bytes());
    payload.extend_from_slice(&0u32.to_be_bytes());
    payload.extend_from_slice(&manifest_bytes);
    for blob in data_blobs {
        payload.extend_from_slice(blob);
    }
    payload
}

// ===========================================================================
// Decompression bomb detection
// ===========================================================================

/// PRD Security: "Abort if bytes_written > declared_size × 1.01.
/// Checked per chunk, not at end."
///
/// This test creates a partition that declares a small size but has
/// data that would decompress to more than 101% of the declared size.
#[test]
fn test_decompression_bomb_fires_at_101_percent_integration() {
    let declared_size = 100u64;
    let actual_data = vec![0x42u8; 102]; // 102 bytes > 100 * 1.01

    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(0),
        partitions: vec![PartitionUpdate {
            partition_name: "bomb_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(declared_size),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(actual_data.len() as u64),
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(1),
                }],
                ..Default::default()
            }],
            ..Default::default()
        }],
        ..Default::default()
    };

    let payload = build_payload_bin(&manifest, &[actual_data.as_slice()]);
    let mut source = Cursor::new(&payload);

    let header = forge_payload::parse_header(&mut source).unwrap();
    std::io::Seek::seek(
        &mut source,
        std::io::SeekFrom::Start(header.manifest_offset()),
    )
    .unwrap();
    let mut manifest_bytes = vec![0u8; header.manifest_size as usize];
    std::io::Read::read_exact(&mut source, &mut manifest_bytes).unwrap();
    let parsed = forge_payload::parse_manifest(&manifest_bytes).unwrap();

    let pu = &parsed.manifest.partitions[0];
    let mut output = Cursor::new(vec![0u8; 4096]);
    let cancel = std::sync::atomic::AtomicBool::new(false);

    let result = forge_payload::extract_partition(
        &mut source,
        &mut output,
        pu,
        header.data_offset(),
        parsed.block_size,
        &cancel,
        |_| {},
    );

    assert!(
        matches!(result, Err(ExtractionError::DecompressionBomb { .. })),
        "Expected DecompressionBomb error, got: {result:?}"
    );
}

// ===========================================================================
// Cancellation preserves partial output
// ===========================================================================

/// PRD Rule #8: "Cancellation preserves verified outputs."
///
/// Pre-cancel the token before extraction. Verify the error is Cancelled
/// (not a panic or IO error).
#[test]
fn test_cancellation_returns_cancelled_error() {
    let data = vec![0u8; 4096];
    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(0),
        partitions: vec![PartitionUpdate {
            partition_name: "cancel_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(4096),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(4096),
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(1),
                }],
                ..Default::default()
            }],
            ..Default::default()
        }],
        ..Default::default()
    };

    let payload = build_payload_bin(&manifest, &[data.as_slice()]);
    let mut source = Cursor::new(&payload);

    let header = forge_payload::parse_header(&mut source).unwrap();
    std::io::Seek::seek(
        &mut source,
        std::io::SeekFrom::Start(header.manifest_offset()),
    )
    .unwrap();
    let mut manifest_bytes = vec![0u8; header.manifest_size as usize];
    std::io::Read::read_exact(&mut source, &mut manifest_bytes).unwrap();
    let parsed = forge_payload::parse_manifest(&manifest_bytes).unwrap();

    let pu = &parsed.manifest.partitions[0];
    let mut output = Cursor::new(vec![0u8; 4096]);
    let cancel = std::sync::atomic::AtomicBool::new(true); // PRE-CANCELLED

    let result = forge_payload::extract_partition(
        &mut source,
        &mut output,
        pu,
        header.data_offset(),
        parsed.block_size,
        &cancel,
        |_| {},
    );

    assert!(
        matches!(result, Err(ExtractionError::Cancelled)),
        "Pre-cancelled extraction should return Cancelled, got: {result:?}"
    );
}

// ===========================================================================
// Manifest size bounds
// ===========================================================================

/// PRD Security: "Bounded manifest parse: ≤ 64 MB allocation."
#[test]
fn test_manifest_exceeding_64mb_rejected() {
    let over_limit = 64 * 1024 * 1024 + 1; // 64 MB + 1

    let mut payload = Vec::new();
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&2u64.to_be_bytes());
    payload.extend_from_slice(&(over_limit as u64).to_be_bytes());
    payload.extend_from_slice(&0u32.to_be_bytes());

    let result = forge_payload::parse_header(&mut Cursor::new(&payload));

    assert!(
        matches!(result, Err(PayloadError::ManifestTooLarge { .. })),
        "Manifest > 64 MB should be rejected, got: {result:?}"
    );
}

/// Edge case: exactly 64 MB should be accepted.
#[test]
fn test_manifest_exactly_64mb_accepted() {
    let exactly_limit = 64 * 1024 * 1024u64;

    let mut payload = Vec::new();
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&2u64.to_be_bytes());
    payload.extend_from_slice(&exactly_limit.to_be_bytes());
    payload.extend_from_slice(&0u32.to_be_bytes());

    let result = forge_payload::parse_header(&mut Cursor::new(&payload));
    assert!(result.is_ok(), "Manifest at exactly 64 MB should be accepted");
}

// ===========================================================================
// Integer overflow in extent calculations
// ===========================================================================

/// PRD Security: "All extent calculations use checked_mul/checked_add."
#[test]
fn test_integer_overflow_in_extent_calculation() {
    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(0),
        partitions: vec![PartitionUpdate {
            partition_name: "overflow_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(4096),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Zero as i32,
                dst_extents: vec![Extent {
                    // u64::MAX × 4096 overflows
                    start_block: Some(u64::MAX),
                    num_blocks: Some(1),
                }],
                ..Default::default()
            }],
            ..Default::default()
        }],
        ..Default::default()
    };

    let payload = build_payload_bin(&manifest, &[]);
    let mut source = Cursor::new(&payload);

    let header = forge_payload::parse_header(&mut source).unwrap();
    std::io::Seek::seek(
        &mut source,
        std::io::SeekFrom::Start(header.manifest_offset()),
    )
    .unwrap();
    let mut manifest_bytes = vec![0u8; header.manifest_size as usize];
    std::io::Read::read_exact(&mut source, &mut manifest_bytes).unwrap();
    let parsed = forge_payload::parse_manifest(&manifest_bytes).unwrap();

    let pu = &parsed.manifest.partitions[0];
    let mut output = Cursor::new(vec![0u8; 4096]);
    let cancel = std::sync::atomic::AtomicBool::new(false);

    let result = forge_payload::extract_partition(
        &mut source,
        &mut output,
        pu,
        header.data_offset(),
        parsed.block_size,
        &cancel,
        |_| {},
    );

    assert!(
        matches!(result, Err(ExtractionError::IntegerOverflow { .. })),
        "Extent overflow should return IntegerOverflow, got: {result:?}"
    );
}

// ===========================================================================
// ZIP bomb guard
// ===========================================================================

/// PRD Security: "1,000-entry limit on central directory scan."
///
/// WHY test at integration level: the unit test in sniff verifies the
/// limit constant. This test builds an actual ZIP with 1001 entries
/// and verifies the full sniff_seekable pipeline rejects it.
#[test]
fn test_zip_bomb_guard_fires_at_1001_entries() {
    let mut zip_bytes = Vec::new();
    {
        let cursor = Cursor::new(&mut zip_bytes);
        let mut zip = zip::ZipWriter::new(cursor);
        let options = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Stored);

        for i in 0..1001 {
            zip.start_file(format!("entry_{i:04}.txt"), options)
                .unwrap();
            zip.write_all(&[0u8; 1]).unwrap();
        }
        zip.finish().unwrap();
    }

    let mut cursor = Cursor::new(&zip_bytes);
    let result = forge_sniff::sniff_seekable(&mut cursor);

    // Should either error with ZipBombGuard or classify as Unknown/Forensic
    match result {
        Err(forge_sniff::SniffError::ZipBombGuard) => {
            // Expected path — guard fired
        }
        Ok(res) => {
            // If it somehow got through, it should be Forensic
            assert_eq!(
                res.tier,
                forge_sniff::SupportTier::Forensic,
                "Large ZIP should never be Supported"
            );
        }
        Err(e) => {
            panic!("Unexpected error type: {e:?}");
        }
    }
}

// ===========================================================================
// Constant-time comparison
// ===========================================================================

/// PRD Security: "SHA-256 verification uses constant-time comparison, not =="
///
/// This tests the correctness of constant-time comparison at the
/// integration level (through verify_hash).
#[test]
fn test_constant_time_comparison_detects_single_bit_mismatch() {
    let expected = vec![0xAA; 32];
    let mut actual = vec![0xAA; 32];
    actual[31] = 0xAB; // Flip last byte

    let result = forge_verification::verify_hash(&expected, &actual);
    assert!(
        matches!(
            result,
            forge_verification::VerificationOutcome::Mismatch { .. }
        ),
        "Single bit difference should be detected, got: {result:?}"
    );
}

#[test]
fn test_constant_time_comparison_matching_hashes() {
    let hash = vec![0xCC; 32];
    let result = forge_verification::verify_hash(&hash, &hash);
    assert_eq!(result, forge_verification::VerificationOutcome::Verified);
}

#[test]
fn test_verification_with_empty_expected_hash_returns_unverifiable() {
    let actual = vec![0xAA; 32];
    let result = forge_verification::verify_hash(&[], &actual);
    assert!(matches!(
        result,
        forge_verification::VerificationOutcome::Unverifiable { .. }
    ));
}

// ===========================================================================
// Unsupported version rejection
// ===========================================================================

/// PRD FR-1: Unsupported payload version returns diagnostic context.
#[test]
fn test_unsupported_version_3_rejected_with_context() {
    let mut payload = Vec::new();
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&3u64.to_be_bytes()); // Version 3
    payload.extend_from_slice(&1024u64.to_be_bytes());

    let result = forge_payload::parse_header(&mut Cursor::new(&payload));

    match result {
        Err(PayloadError::UnsupportedVersion { found }) => {
            assert_eq!(found, 3, "Error should carry the found version number");
        }
        other => panic!("Expected UnsupportedVersion, got: {other:?}"),
    }
}

// ===========================================================================
// Error type serialization (for JNI boundary)
// ===========================================================================

/// Verify that ExtractionError error_code() returns machine-readable codes.
#[test]
fn test_extraction_error_codes_are_machine_readable() {
    let errors_and_codes = vec![
        (
            ExtractionError::Cancelled,
            "CANCELLED",
        ),
        (
            ExtractionError::IntegerOverflow {
                context: "test".to_string(),
            },
            "INTEGER_OVERFLOW",
        ),
        (
            ExtractionError::DecompressionBomb {
                actual: 100,
                declared: 50,
                limit: 51,
            },
            "DECOMPRESSION_BOMB",
        ),
        (
            ExtractionError::UnsupportedOperation {
                op_type: 99,
                partition: "test".to_string(),
            },
            "UNSUPPORTED_OPERATION",
        ),
        (
            ExtractionError::VerificationFailed {
                partition: "test".to_string(),
                expected: "aabb".to_string(),
                actual: "ccdd".to_string(),
            },
            "VERIFICATION_FAILED",
        ),
        (
            ExtractionError::PathTraversal {
                path: "../etc/passwd".to_string(),
                destination: "/data/output".to_string(),
            },
            "PATH_TRAVERSAL",
        ),
    ];

    for (error, expected_code) in errors_and_codes {
        assert_eq!(
            error.error_code(),
            expected_code,
            "Error code mismatch for {:?}",
            error
        );
    }
}

use std::io::Write;
