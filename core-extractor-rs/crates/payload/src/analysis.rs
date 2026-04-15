//! AnalysisResult construction for JNI serialization.
//!
//! Combines header parsing + manifest parsing into a single analysis
//! result that crosses the JNI boundary as JSON.

use crate::error::PayloadError;
use crate::header::{parse_header, PayloadHeader};
use crate::manifest::parse_manifest;
use crate::types::PartitionSummary;
use forge_sniff::{PackageFamily, SupportTier};
use serde::Serialize;
use std::io::{Read, Seek, SeekFrom};

/// Full analysis result that crosses JNI as JSON.
///
/// This is the primary output of the `analyze` JNI function. It contains
/// everything the Kotlin UI needs to render the analysis screen.
#[derive(Debug, Clone, Serialize)]
pub struct AnalysisResult {
    /// Package family classification (e.g., "AospPayloadOta").
    pub package_family: String,
    /// Support tier (e.g., "Supported", "Experimental", "Forensic").
    pub support_tier: String,
    /// Whether this is an incremental/delta package.
    pub is_incremental: bool,
    /// Payload major version.
    pub major_version: u64,
    /// Payload minor version (0 = full, >0 = delta).
    pub minor_version: u32,
    /// Block size in bytes (usually 4096).
    pub block_size: u32,
    /// Partition inventory.
    pub partitions: Vec<PartitionSummary>,
    /// Security patch level from manifest metadata.
    pub security_patch_level: Option<String>,
    /// Total payload size in bytes (for progress estimation).
    pub total_payload_size: u64,
    /// Manifest size in bytes.
    pub manifest_size: u64,
    /// Number of partitions.
    pub partition_count: usize,
    /// Maximum timestamp from manifest.
    pub max_timestamp: Option<i64>,
}

/// Analyze a payload.bin file from a seekable reader.
///
/// Performs the full analysis pipeline:
/// 1. Parse CrAU header (with security bounds)
/// 2. Read and parse DeltaArchiveManifest protobuf
/// 3. Build partition inventory and classification
///
/// # Errors
/// Returns `PayloadError` with specific diagnostic context.
pub fn analyze<R: Read + Seek + ?Sized>(
    reader: &mut R,
    family: PackageFamily,
    tier: SupportTier,
) -> Result<AnalysisResult, PayloadError> {
    // Step 1: Parse the CrAU header
    let header = parse_header(reader)?;

    // Step 2: Read the manifest bytes
    let manifest_data = read_manifest(reader, &header)?;

    // Step 3: Parse the manifest
    let parsed = parse_manifest(&manifest_data)?;

    // Step 4: Determine total payload size from reader position
    let total_payload_size = reader.seek(SeekFrom::End(0)).map_err(|e| PayloadError::Io {
        context: "seeking to end for total size".to_string(),
        source: e,
    })?;

    let partition_count = parsed.partitions.len();

    // Adjust tier based on incremental status
    let effective_tier = if parsed.is_incremental {
        SupportTier::Experimental
    } else {
        tier
    };

    Ok(AnalysisResult {
        package_family: format!("{family}"),
        support_tier: format!("{effective_tier}"),
        is_incremental: parsed.is_incremental,
        major_version: header.major_version,
        minor_version: parsed.minor_version,
        block_size: parsed.block_size,
        partitions: parsed.partitions,
        security_patch_level: parsed.security_patch_level,
        total_payload_size,
        manifest_size: header.manifest_size,
        partition_count,
        max_timestamp: parsed.max_timestamp,
    })
}

/// Read the manifest protobuf bytes from the payload.
fn read_manifest<R: Read + Seek + ?Sized>(
    reader: &mut R,
    header: &PayloadHeader,
) -> Result<Vec<u8>, PayloadError> {
    // Seek to manifest offset (right after the header)
    reader
        .seek(SeekFrom::Start(header.manifest_offset()))
        .map_err(|e| PayloadError::Io {
            context: "seeking to manifest offset".to_string(),
            source: e,
        })?;

    // WHY usize cast is safe: manifest_size is bounded to 64 MB by header parser,
    // which fits comfortably in usize on both 32-bit and 64-bit platforms.
    let manifest_len = header.manifest_size as usize;
    let mut manifest_data = vec![0u8; manifest_len];
    reader.read_exact(&mut manifest_data).map_err(|e| {
        if e.kind() == std::io::ErrorKind::UnexpectedEof {
            PayloadError::ManifestCorrupt {
                details: format!(
                    "File truncated: expected {} manifest bytes, got fewer",
                    manifest_len
                ),
            }
        } else {
            PayloadError::Io {
                context: "reading manifest data".to_string(),
                source: e,
            }
        }
    })?;

    Ok(manifest_data)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{DeltaArchiveManifest, Extent, InstallOperation, OpType, PartitionInfo, PartitionUpdate};
    use prost::Message;
    use std::io::Cursor;

    /// Build a complete synthetic payload.bin for testing.
    fn make_synthetic_payload(manifest: &DeltaArchiveManifest) -> Vec<u8> {
        let manifest_bytes = manifest.encode_to_vec();
        let manifest_size = manifest_bytes.len() as u64;
        let sig_size = 0u32;

        let mut payload = Vec::new();
        // Header
        payload.extend_from_slice(b"CrAU");
        payload.extend_from_slice(&2u64.to_be_bytes());
        payload.extend_from_slice(&manifest_size.to_be_bytes());
        payload.extend_from_slice(&sig_size.to_be_bytes());
        // Manifest
        payload.extend_from_slice(&manifest_bytes);
        // Dummy data blob
        payload.extend_from_slice(&[0u8; 1024]);

        payload
    }

    #[test]
    fn test_analyze_synthetic_full_payload() {
        let manifest = DeltaArchiveManifest {
            block_size: Some(4096),
            minor_version: Some(0),
            partitions: vec![PartitionUpdate {
                partition_name: "boot".to_string(),
                new_partition_info: Some(PartitionInfo {
                    size: Some(65536),
                    hash: Some(vec![0xAA; 32]),
                }),
                operations: vec![InstallOperation {
                    r#type: OpType::Replace as i32,
                    data_offset: Some(0),
                    data_length: Some(65536),
                    dst_extents: vec![Extent {
                        start_block: Some(0),
                        num_blocks: Some(16),
                    }],
                    ..Default::default()
                }],
                ..Default::default()
            }],
            security_patch_level: Some("2026-04-05".to_string()),
            ..Default::default()
        };

        let payload = make_synthetic_payload(&manifest);
        let mut cursor = Cursor::new(payload);

        let result = analyze(
            &mut cursor,
            PackageFamily::StandalonePayload,
            SupportTier::Supported,
        )
        .unwrap();

        assert_eq!(result.support_tier, "Supported");
        assert!(!result.is_incremental);
        assert_eq!(result.major_version, 2);
        assert_eq!(result.minor_version, 0);
        assert_eq!(result.partition_count, 1);
        assert_eq!(result.partitions[0].name, "boot");
        assert_eq!(result.security_patch_level, Some("2026-04-05".to_string()));
    }

    #[test]
    fn test_analyze_incremental_payload_sets_experimental_tier() {
        let manifest = DeltaArchiveManifest {
            block_size: Some(4096),
            minor_version: Some(5), // incremental
            partitions: vec![PartitionUpdate {
                partition_name: "system".to_string(),
                operations: vec![],
                ..Default::default()
            }],
            ..Default::default()
        };

        let payload = make_synthetic_payload(&manifest);
        let mut cursor = Cursor::new(payload);

        let result = analyze(
            &mut cursor,
            PackageFamily::AospPayloadOta,
            SupportTier::Supported,
        )
        .unwrap();

        // Incremental payloads are always Experimental tier
        assert_eq!(result.support_tier, "Experimental");
        assert!(result.is_incremental);
    }

    #[test]
    fn test_analyze_serializes_to_json() {
        let manifest = DeltaArchiveManifest {
            block_size: Some(4096),
            minor_version: Some(0),
            partitions: vec![],
            ..Default::default()
        };

        let payload = make_synthetic_payload(&manifest);
        let mut cursor = Cursor::new(payload);

        let result = analyze(
            &mut cursor,
            PackageFamily::StandalonePayload,
            SupportTier::Supported,
        )
        .unwrap();

        let json = serde_json::to_string(&result).unwrap();
        assert!(json.contains("Supported"));
        assert!(json.contains("Standalone Payload"));
    }
}
