package dev.forgeotalab.contracts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Format report for FR-12 (Format Report Export).
 *
 * WHY separate from DiagnosticsBundle: DiagnosticsBundle is job-scoped and
 * includes failure context. FormatReport is package-scoped — it documents
 * what was detected, not what went wrong. Users export this for community
 * debugging or adapter support requests.
 *
 * PRD FR-12: "Export JSON report: package classification, support tier,
 * build metadata, partition list, checksum metadata when available,
 * app version, adapter version."
 */
@Serializable
data class FormatReport(
    /** Schema version for forward compatibility. */
    @SerialName("schema_version")
    val schemaVersion: Int = 1,

    /** Epoch millis when this report was generated. */
    @SerialName("generated_at")
    val generatedAt: Long,

    /** App version that generated this report. */
    @SerialName("app_version")
    val appVersion: String,

    /** Package classification. */
    val classification: String,

    /** Support tier. */
    @SerialName("support_tier")
    val supportTier: String,

    /** Detected package family. */
    @SerialName("package_family")
    val packageFamily: String,

    /** Whether this is incremental. */
    @SerialName("is_incremental")
    val isIncremental: Boolean,

    /** Slot model. */
    @SerialName("slot_model")
    val slotModel: String,

    /** Build metadata. */
    @SerialName("build_metadata")
    val buildMetadata: BuildMetadata? = null,

    /** Partition summary list. */
    val partitions: List<PartitionSummary>,

    /** Adapter that classified this package. */
    @SerialName("adapter_info")
    val adapterInfo: AdapterInfo? = null,
)

@Serializable
data class BuildMetadata(
    /** Target build fingerprint. */
    @SerialName("target_fingerprint")
    val targetFingerprint: String? = null,

    /** Source build fingerprint (incremental only). */
    @SerialName("source_fingerprint")
    val sourceFingerprint: String? = null,

    /** Security patch level. */
    @SerialName("security_patch_level")
    val securityPatchLevel: String? = null,

    /** Device model from manifest. */
    @SerialName("device_model")
    val deviceModel: String? = null,
)

@Serializable
data class PartitionSummary(
    /** Partition name. */
    val name: String,

    /** Category. */
    val category: String,

    /** Target size in bytes. */
    @SerialName("target_size")
    val targetSize: Long,

    /** Target SHA-256 hash (hex) — null if unavailable. */
    @SerialName("target_hash")
    val targetHash: String? = null,

    /** Operation count. */
    @SerialName("operation_count")
    val operationCount: Int,

    /** Whether partition has source-dependent ops. */
    @SerialName("has_source_ops")
    val hasSourceOps: Boolean,
)
