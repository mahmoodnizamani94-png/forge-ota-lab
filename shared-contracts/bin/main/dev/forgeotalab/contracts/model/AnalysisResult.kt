package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Support tier classification for OTA packages.
 *
 * WHY: This enum is the single source of truth for how the UI presents package
 * confidence. The Rust core sets this value — the Kotlin UI never decides
 * tier classification independently (PRD Non-Negotiable Rule #1).
 */
@Serializable
enum class SupportTier {
    SUPPORTED,
    EXPERIMENTAL,
    FORENSIC,
}

/**
 * Derivation type for extracted artifacts.
 *
 * WHY: Users need to know whether an output is a direct extract, a reconstruction
 * from delta operations, a partial result, or an unverified raw dump.
 * PRD Non-Negotiable Rule #5: incremental outputs always carry RECONSTRUCTED
 * or RAW_UNVERIFIED — never labeled as a direct extract.
 */
@Serializable
enum class DerivationType {
    DIRECT,
    RECONSTRUCTED,
    PARTIAL,
    RAW_UNVERIFIED,
}

/**
 * Full analysis result matching the Rust core's JSON output.
 *
 * This is the primary output from the `NativeBridge.analyze()` JNI call.
 * It contains everything the Kotlin UI needs to render the analysis screen,
 * partition selection UI, and storage estimates.
 *
 * WHY separate from PackageEntity: PackageEntity is the Room persistence model
 * with database-friendly flat fields. AnalysisResult is the rich domain model
 * carrying nested partition details that get mapped to separate Room entities.
 *
 * Field naming uses Kotlin conventions (camelCase). The Rust core serializes
 * with snake_case — deserialization uses @SerialName mappings.
 */
@Serializable
data class AnalysisResult(
    /** Package family (e.g., "AospPayloadOta", "Standalone Payload"). */
    @kotlinx.serialization.SerialName("package_family")
    val packageFamily: String,

    /** Support tier — set exclusively by Rust core adapter output. */
    @kotlinx.serialization.SerialName("support_tier")
    val supportTier: String,

    /** Whether this is an incremental/delta package. */
    @kotlinx.serialization.SerialName("is_incremental")
    val isIncremental: Boolean,

    /** Payload major version (expected: 2). */
    @kotlinx.serialization.SerialName("major_version")
    val majorVersion: Long,

    /** Payload minor version (0 = full, >0 = delta). */
    @kotlinx.serialization.SerialName("minor_version")
    val minorVersion: Int,

    /** Block size in bytes (usually 4096). */
    @kotlinx.serialization.SerialName("block_size")
    val blockSize: Int,

    /** Full partition inventory. */
    val partitions: List<PartitionInfo>,

    /** Security patch level from manifest metadata. */
    @kotlinx.serialization.SerialName("security_patch_level")
    val securityPatchLevel: String? = null,

    /** Total payload size in bytes. */
    @kotlinx.serialization.SerialName("total_payload_size")
    val totalPayloadSize: Long,

    /** Manifest size in bytes. */
    @kotlinx.serialization.SerialName("manifest_size")
    val manifestSize: Long,

    /** Number of partitions. */
    @kotlinx.serialization.SerialName("partition_count")
    val partitionCount: Int,

    /** Maximum timestamp from manifest. */
    @kotlinx.serialization.SerialName("max_timestamp")
    val maxTimestamp: Long? = null,
) {
    /**
     * Map the string-based support tier from Rust to the typed enum.
     * WHY a computed property: The Rust side serializes tier as a string
     * ("Supported", "Experimental", "Forensic"). This bridges the gap.
     */
    val supportTierEnum: SupportTier
        get() = when (supportTier) {
            "Supported" -> SupportTier.SUPPORTED
            "Experimental" -> SupportTier.EXPERIMENTAL
            else -> SupportTier.FORENSIC
        }
}

/**
 * Summary of a single partition from the parsed manifest.
 *
 * Mirrors Rust's `PartitionSummary` struct for JNI JSON serialization.
 * Used to build the partition selection UI (FR-6).
 */
@Serializable
data class PartitionInfo(
    /** Partition name (e.g., "boot", "system", "vendor"). */
    val name: String,

    /** Category for grouping: "boot_critical", "logical_system", etc. */
    val category: String,

    /** Target (output) partition size in bytes. */
    @kotlinx.serialization.SerialName("target_size")
    val targetSize: Long,

    /** SHA-256 hash of the target partition (hex). */
    @kotlinx.serialization.SerialName("target_hash")
    val targetHash: String? = null,

    /** SHA-256 hash of the source partition (hex) — incremental only. */
    @kotlinx.serialization.SerialName("source_hash")
    val sourceHash: String? = null,

    /** Source partition size in bytes — incremental only. */
    @kotlinx.serialization.SerialName("source_size")
    val sourceSize: Long? = null,

    /** Number of InstallOperations for this partition. */
    @kotlinx.serialization.SerialName("operation_count")
    val operationCount: Int,

    /** Whether this partition has source-dependent operations (incremental). */
    @kotlinx.serialization.SerialName("has_source_ops")
    val hasSourceOps: Boolean,

    /** Detailed operation summaries. */
    val operations: List<OperationInfo> = emptyList(),

    /** Partition version string from manifest. */
    val version: String? = null,
) {
    /**
     * Map the string-based category to the typed enum.
     */
    val categoryEnum: PartitionCategory
        get() = when (category) {
            "boot_critical" -> PartitionCategory.BOOT_CRITICAL
            "logical_system" -> PartitionCategory.LOGICAL_SYSTEM
            "firmware_radio" -> PartitionCategory.FIRMWARE_RADIO
            "metadata" -> PartitionCategory.METADATA
            else -> PartitionCategory.MISC
        }

    /**
     * Whether this partition can be extracted from a full OTA.
     * Partitions with source-dependent operations require base inputs.
     */
    val isExtractable: Boolean
        get() = !hasSourceOps

    /**
     * Human-readable reason if partition is not extractable.
     */
    val notExtractableReason: String?
        get() = if (hasSourceOps) "Requires base image (incremental operations)" else null

    /**
     * Dominant compression algorithm for display purposes.
     */
    val compressionAlgorithm: String?
        get() = operations.firstOrNull()?.compression
}

/**
 * Summary of a single InstallOperation for diagnostic display.
 *
 * Mirrors Rust's `OperationSummary` struct.
 */
@Serializable
data class OperationInfo(
    /** Operation type name ("Replace", "ReplaceXz", "Zero", etc.). */
    @kotlinx.serialization.SerialName("op_type")
    val opType: String,

    /** Compression algorithm if applicable. */
    val compression: String? = null,

    /** Compressed data size in payload. */
    @kotlinx.serialization.SerialName("data_length")
    val dataLength: Long,

    /** Number of destination extent blocks. */
    @kotlinx.serialization.SerialName("dst_blocks")
    val dstBlocks: Long,
)
