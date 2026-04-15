//! Domain types for analysis and extraction results.
//!
//! These types are the Rust-side representation of analysis data
//! that crosses the JNI boundary as JSON.

use serde::Serialize;

// ---------------------------------------------------------------------------
// Partition summary (analysis output)
// ---------------------------------------------------------------------------

/// Summary of a single partition from the parsed manifest.
///
/// This is the analysis-phase representation — it describes what's
/// in the manifest, not what was extracted. Used to build the partition
/// selection UI on the Kotlin side.
#[derive(Debug, Clone, Serialize)]
pub struct PartitionSummary {
    /// Partition name (e.g., "boot", "system", "vendor").
    pub name: String,
    /// Category for grouping in the UI (boot_critical, logical_system, etc.).
    pub category: String,
    /// Target (output) partition size in bytes, from new_partition_info.
    pub target_size: u64,
    /// SHA-256 hash of the target partition (hex), from new_partition_info.
    pub target_hash: Option<String>,
    /// SHA-256 hash of the source partition (hex), from old_partition_info.
    /// Present only for incremental operations.
    pub source_hash: Option<String>,
    /// Source partition size in bytes, from old_partition_info.
    pub source_size: Option<u64>,
    /// Number of InstallOperations for this partition.
    pub operation_count: usize,
    /// Whether this partition has source-dependent operations (incremental).
    pub has_source_ops: bool,
    /// Summary of each operation for diagnostic/advanced display.
    pub operations: Vec<OperationSummary>,
    /// Partition version string from the manifest (build timestamp).
    pub version: Option<String>,
}

/// Summary of a single InstallOperation for diagnostic display.
#[derive(Debug, Clone, Serialize)]
pub struct OperationSummary {
    /// Operation type name (REPLACE, REPLACE_XZ, ZERO, etc.).
    pub op_type: String,
    /// Compression algorithm if applicable.
    pub compression: Option<CompressionAlgorithm>,
    /// Compressed data size in payload (data_length field).
    pub data_length: u64,
    /// Number of destination extent blocks.
    pub dst_blocks: u64,
}

// ---------------------------------------------------------------------------
// Compression algorithm
// ---------------------------------------------------------------------------

/// Compression algorithm used within a REPLACE operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub enum CompressionAlgorithm {
    None,
    Xz,
    Bzip2,
    Zstd,
}

// ---------------------------------------------------------------------------
// Partition category classification
// ---------------------------------------------------------------------------

/// Classify a partition name into a UI category.
///
/// PRD FR-6: Group partitions into boot-critical, logical system,
/// firmware/radio, metadata, misc.
pub fn classify_partition(name: &str) -> &'static str {
    let lower = name.to_lowercase();
    match lower.as_str() {
        "boot" | "init_boot" | "vendor_boot" | "vbmeta" | "vbmeta_system"
        | "vbmeta_vendor" | "dtbo" | "recovery" => "boot_critical",

        "system" | "system_ext" | "vendor" | "product" | "odm" | "system_dlkm"
        | "vendor_dlkm" | "odm_dlkm" => "logical_system",

        "modem" | "radio" | "abl" | "xbl" | "tz" | "hyp" | "devcfg" | "aop"
        | "bluetooth" | "dsp" | "keymaster" | "cmnlib" | "cmnlib64" => "firmware_radio",

        "metadata" | "userdata" | "persist" | "misc" | "frp" => "metadata",

        "super" => "logical_system",

        _ => "misc",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_boot_partitions_classified_as_boot_critical() {
        assert_eq!(classify_partition("boot"), "boot_critical");
        assert_eq!(classify_partition("init_boot"), "boot_critical");
        assert_eq!(classify_partition("vbmeta"), "boot_critical");
        assert_eq!(classify_partition("dtbo"), "boot_critical");
        assert_eq!(classify_partition("recovery"), "boot_critical");
    }

    #[test]
    fn test_system_partitions_classified_as_logical() {
        assert_eq!(classify_partition("system"), "logical_system");
        assert_eq!(classify_partition("vendor"), "logical_system");
        assert_eq!(classify_partition("product"), "logical_system");
    }

    #[test]
    fn test_unknown_partition_classified_as_misc() {
        assert_eq!(classify_partition("something_custom"), "misc");
    }

    #[test]
    fn test_case_insensitive_classification() {
        assert_eq!(classify_partition("BOOT"), "boot_critical");
        assert_eq!(classify_partition("System"), "logical_system");
    }

    #[test]
    fn test_compression_algorithm_serializes() {
        let algo = CompressionAlgorithm::Xz;
        let json = serde_json::to_string(&algo).unwrap();
        assert!(json.contains("Xz"));
    }
}
