//! Manifest parsing and partition inventory construction.
//!
//! Deserializes the DeltaArchiveManifest protobuf from payload.bin,
//! classifies full vs incremental from minor_version, and builds
//! the partition inventory used by the analysis screen.
//!
//! Security bounds:
//! - Manifest data must not exceed 64 MB (enforced in header parser)
//! - Parse timeout: 10-second check around protobuf decode

use crate::error::PayloadError;
use crate::proto::{DeltaArchiveManifest, OpType};
use crate::types::{
    classify_partition, CompressionAlgorithm, OperationSummary, PartitionSummary,
};
use forge_verification::hex_encode;
use prost::Message;
use std::time::Instant;

/// Maximum parse duration before timeout: 10 seconds.
/// PRD Security: "10-second timeout."
const MAX_PARSE_DURATION_MS: u64 = 10_000;

/// Result of parsing the DeltaArchiveManifest.
#[derive(Debug, Clone)]
pub struct ParsedManifest {
    /// The raw protobuf manifest (for extraction use).
    pub manifest: DeltaArchiveManifest,
    /// Whether this is an incremental/delta payload (minor_version != 0).
    pub is_incremental: bool,
    /// Minor version from the manifest.
    pub minor_version: u32,
    /// Block size (default 4096).
    pub block_size: u32,
    /// Parsed partition summaries for the analysis screen.
    pub partitions: Vec<PartitionSummary>,
    /// Security patch level from manifest, if present.
    pub security_patch_level: Option<String>,
    /// Maximum timestamp, if present.
    pub max_timestamp: Option<i64>,
}

/// Parse the DeltaArchiveManifest from raw protobuf bytes.
///
/// Security:
/// - Input size already bounded to 64 MB by the header parser.
/// - Parse timeout: aborts if decode takes > 10 seconds.
///
/// WHY minor_version check: In the AOSP payload format, minor_version == 0
/// means full payload (no delta operations). Any other minor_version means
/// delta/incremental payload requiring source partition data.
pub fn parse_manifest(data: &[u8]) -> Result<ParsedManifest, PayloadError> {
    let start = Instant::now();

    // Decode the protobuf
    let manifest = DeltaArchiveManifest::decode(data)?;

    // Check for timeout after decode
    let elapsed = start.elapsed();
    if elapsed.as_millis() > MAX_PARSE_DURATION_MS as u128 {
        return Err(PayloadError::ParseTimeout {
            elapsed_ms: elapsed.as_millis() as u64,
            limit_ms: MAX_PARSE_DURATION_MS,
        });
    }

    let minor_version = manifest.minor_version();
    let is_incremental = minor_version != 0;
    let block_size = manifest.block_size();
    let security_patch_level = manifest.security_patch_level.clone();
    let max_timestamp = manifest.max_timestamp;

    // Build partition summaries
    let partitions = build_partition_summaries(&manifest);

    Ok(ParsedManifest {
        manifest,
        is_incremental,
        minor_version,
        block_size,
        partitions,
        security_patch_level,
        max_timestamp,
    })
}

/// Build partition summaries from the manifest's PartitionUpdate list.
fn build_partition_summaries(manifest: &DeltaArchiveManifest) -> Vec<PartitionSummary> {
    manifest
        .partitions
        .iter()
        .map(|pu| {
            let name = pu.partition_name.clone();
            let category = classify_partition(&name).to_string();

            // Extract target partition info (new_partition_info)
            let (target_size, target_hash) = match &pu.new_partition_info {
                Some(info) => (
                    info.size.unwrap_or(0),
                    info.hash.as_ref().map(|h| hex_encode(h)),
                ),
                None => (0, None),
            };

            // Extract source partition info (old_partition_info, for incremental)
            let (source_size, source_hash) = match &pu.old_partition_info {
                Some(info) => (
                    Some(info.size.unwrap_or(0)),
                    info.hash.as_ref().map(|h| hex_encode(h)),
                ),
                None => (None, None),
            };

            // WHY has_source_ops: determines if this partition requires source data
            // for extraction (incremental). Operations like SOURCE_COPY, SOURCE_BSDIFF,
            // BSDIFF, PUFFDIFF all require source partition data.
            let has_source_ops = pu.operations.iter().any(|op| {
                matches!(
                    OpType::try_from(op.r#type),
                    Ok(OpType::SourceCopy)
                        | Ok(OpType::SourceBsdiff)
                        | Ok(OpType::Bsdiff)
                        | Ok(OpType::Puffdiff)
                        | Ok(OpType::BrotliBsdiff)
                        | Ok(OpType::Zucchini)
                        | Ok(OpType::Lz4diffBsdiff)
                        | Ok(OpType::Lz4diffPuffdiff)
                )
            });

            let operations: Vec<OperationSummary> = pu
                .operations
                .iter()
                .map(build_operation_summary)
                .collect();

            PartitionSummary {
                name,
                category,
                target_size,
                target_hash,
                source_hash,
                source_size,
                operation_count: pu.operations.len(),
                has_source_ops,
                operations,
                version: pu.version.clone(),
            }
        })
        .collect()
}

/// Build a summary for a single InstallOperation.
fn build_operation_summary(
    op: &crate::proto::InstallOperation,
) -> OperationSummary {
    let op_type_enum = OpType::try_from(op.r#type).ok();
    let op_type = match op_type_enum {
        Some(t) => format!("{t:?}"),
        None => format!("Unknown({})", op.r#type),
    };

    let compression = op_type_enum.and_then(|t| match t {
        OpType::Replace => Some(CompressionAlgorithm::None),
        OpType::ReplaceBz => Some(CompressionAlgorithm::Bzip2),
        OpType::ReplaceXz => Some(CompressionAlgorithm::Xz),
        _ => None,
    });

    let dst_blocks: u64 = op
        .dst_extents
        .iter()
        .map(|e| e.num_blocks.unwrap_or(0))
        .sum();

    OperationSummary {
        op_type,
        compression,
        data_length: op.data_length.unwrap_or(0),
        dst_blocks,
    }
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{Extent, InstallOperation, PartitionInfo, PartitionUpdate};
    use prost::Message;

    /// Build a synthetic DeltaArchiveManifest for testing.
    fn make_test_manifest(
        minor_version: u32,
        partitions: Vec<PartitionUpdate>,
    ) -> DeltaArchiveManifest {
        DeltaArchiveManifest {
            block_size: Some(4096),
            minor_version: Some(minor_version),
            partitions,
            security_patch_level: Some("2026-04-05".to_string()),
            ..Default::default()
        }
    }

    /// Build a simple partition with REPLACE operations.
    fn make_partition(name: &str, size: u64) -> PartitionUpdate {
        PartitionUpdate {
            partition_name: name.to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(size),
                hash: Some(vec![0xAA; 32]),
            }),
            old_partition_info: None,
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(size),
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(size / 4096),
                }],
                ..Default::default()
            }],
            ..Default::default()
        }
    }

    #[test]
    fn test_full_payload_detected_from_minor_version_zero() {
        let manifest = make_test_manifest(0, vec![make_partition("boot", 65536)]);
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert!(!parsed.is_incremental);
        assert_eq!(parsed.minor_version, 0);
    }

    #[test]
    fn test_incremental_payload_detected_from_nonzero_minor_version() {
        let manifest = make_test_manifest(5, vec![make_partition("system", 1024 * 1024)]);
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert!(parsed.is_incremental);
        assert_eq!(parsed.minor_version, 5);
    }

    #[test]
    fn test_partition_summaries_built_correctly() {
        let manifest = make_test_manifest(
            0,
            vec![
                make_partition("boot", 65536),
                make_partition("system", 1024 * 1024),
            ],
        );
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert_eq!(parsed.partitions.len(), 2);
        assert_eq!(parsed.partitions[0].name, "boot");
        assert_eq!(parsed.partitions[0].category, "boot_critical");
        assert_eq!(parsed.partitions[0].target_size, 65536);
        assert!(parsed.partitions[0].target_hash.is_some());
        assert_eq!(parsed.partitions[1].name, "system");
        assert_eq!(parsed.partitions[1].category, "logical_system");
    }

    #[test]
    fn test_security_patch_level_preserved() {
        let manifest = make_test_manifest(0, vec![]);
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert_eq!(
            parsed.security_patch_level,
            Some("2026-04-05".to_string())
        );
    }

    #[test]
    fn test_corrupted_protobuf_returns_structured_error() {
        let garbage = vec![0xFF, 0xFE, 0xFD, 0xFC, 0xFB, 0xFA];
        let result = parse_manifest(&garbage);
        assert!(result.is_err());
        // Should be a ProtobufDecode error, not a panic
        assert!(matches!(
            result,
            Err(PayloadError::ProtobufDecode(_))
                | Err(PayloadError::ManifestCorrupt { .. })
        ));
    }

    #[test]
    fn test_empty_manifest_parses_with_defaults() {
        let manifest = DeltaArchiveManifest::default();
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert_eq!(parsed.block_size, 4096); // default
        assert_eq!(parsed.minor_version, 0); // default
        assert!(!parsed.is_incremental);
        assert!(parsed.partitions.is_empty());
    }

    #[test]
    fn test_source_ops_detected_for_incremental_partition() {
        let mut partition = make_partition("system", 1024 * 1024);
        // Add a SOURCE_COPY operation
        partition.operations.push(InstallOperation {
            r#type: OpType::SourceCopy as i32,
            src_extents: vec![Extent {
                start_block: Some(0),
                num_blocks: Some(256),
            }],
            dst_extents: vec![Extent {
                start_block: Some(0),
                num_blocks: Some(256),
            }],
            ..Default::default()
        });
        partition.old_partition_info = Some(PartitionInfo {
            size: Some(1024 * 1024),
            hash: Some(vec![0xBB; 32]),
        });

        let manifest = make_test_manifest(5, vec![partition]);
        let data = manifest.encode_to_vec();
        let parsed = parse_manifest(&data).unwrap();

        assert!(parsed.partitions[0].has_source_ops);
        assert!(parsed.partitions[0].source_hash.is_some());
    }
}
