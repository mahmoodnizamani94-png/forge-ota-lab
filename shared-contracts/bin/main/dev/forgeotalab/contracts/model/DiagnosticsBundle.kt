package dev.forgeotalab.contracts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured diagnostics bundle for FR-10 (Diagnostics Export).
 *
 * WHY serializable: The bundle is written as JSON, zipped, and shared via
 * FileProvider. Automated tooling on the support side parses this structure
 * to reconstruct failure context without accessing the user's OTA file.
 *
 * PRD: "Exportable diagnostics bundle includes: package fingerprint, adapter ID
 * and version, support tier, job phase at failure, partition-level failure list,
 * environment summary, and anonymized logs. The bundle excludes package contents
 * and raw file paths by default."
 */
@Serializable
data class DiagnosticsBundle(
    /** Schema version for forward compatibility. */
    @SerialName("schema_version")
    val schemaVersion: Int = 1,

    /** Epoch millis when this bundle was generated. */
    @SerialName("generated_at")
    val generatedAt: Long,

    /** App version that generated this bundle. */
    @SerialName("app_version")
    val appVersion: String,

    /** Package fingerprint (detected magic bytes + file size hash). */
    @SerialName("package_fingerprint")
    val packageFingerprint: PackageFingerprint,

    /** Adapter that classified the package — null for unrecognized packages. */
    @SerialName("adapter_info")
    val adapterInfo: AdapterInfo? = null,

    /** Support tier at time of failure. */
    @SerialName("support_tier")
    val supportTier: String,

    /** Job-level failure summary — null if diagnostics are package-only. */
    @SerialName("job_failure")
    val jobFailure: JobFailureInfo? = null,

    /** Per-partition failure details — empty if no partition-level failures. */
    @SerialName("partition_failures")
    val partitionFailures: List<PartitionFailureInfo> = emptyList(),

    /** Device and environment context. */
    val environment: EnvironmentInfo,

    /** Sections that could not be generated (e.g., DB corruption). */
    @SerialName("missing_sections")
    val missingSections: List<String> = emptyList(),
)

@Serializable
data class PackageFingerprint(
    /** Detected file size in bytes. */
    @SerialName("file_size_bytes")
    val fileSizeBytes: Long,

    /** First N magic bytes as hex string. */
    @SerialName("magic_bytes")
    val magicBytes: String? = null,

    /** Detected package family. */
    @SerialName("package_family")
    val packageFamily: String,

    /** Package classification. */
    val classification: String,

    /** Whether this is an incremental package. */
    @SerialName("is_incremental")
    val isIncremental: Boolean,

    /** Slot model. */
    @SerialName("slot_model")
    val slotModel: String,

    /** Partition count from analysis. */
    @SerialName("partition_count")
    val partitionCount: Int? = null,
)

@Serializable
data class AdapterInfo(
    /** Adapter ID (e.g., "google_pixel_full_v1"). */
    @SerialName("adapter_id")
    val adapterId: String,

    /** Adapter version. */
    @SerialName("adapter_version")
    val adapterVersion: String,
)

@Serializable
data class JobFailureInfo(
    /** Job ID (UUID). */
    @SerialName("job_id")
    val jobId: String,

    /** Job status at time of bundle generation. */
    val status: String,

    /** Phase where failure occurred. */
    @SerialName("failure_phase")
    val failurePhase: String? = null,

    /** Serialized error summary. */
    @SerialName("error_summary")
    val errorSummary: String? = null,

    /** Total partitions selected. */
    @SerialName("total_partitions")
    val totalPartitions: Int,

    /** Completed partitions. */
    @SerialName("completed_partitions")
    val completedPartitions: Int,

    /** Failed partitions. */
    @SerialName("failed_partitions")
    val failedPartitions: Int,

    /** Job duration in milliseconds — null if still running. */
    @SerialName("duration_ms")
    val durationMs: Long? = null,
)

@Serializable
data class PartitionFailureInfo(
    /** Partition name (e.g., "boot", "system"). */
    @SerialName("partition_name")
    val partitionName: String,

    /** Phase where this partition failed. */
    @SerialName("failure_phase")
    val failurePhase: String,

    /** Error class/type. */
    @SerialName("error_class")
    val errorClass: String,

    /** Error details — diagnostic context, no raw paths. */
    @SerialName("error_details")
    val errorDetails: String? = null,
)

@Serializable
data class EnvironmentInfo(
    /** Device manufacturer. */
    val manufacturer: String,

    /** Device model. */
    val model: String,

    /** Android version string (e.g., "14"). */
    @SerialName("android_version")
    val androidVersion: String,

    /** API level. */
    @SerialName("api_level")
    val apiLevel: Int,

    /** Android security patch level. */
    @SerialName("security_patch")
    val securityPatch: String? = null,

    /** Available storage in bytes. */
    @SerialName("available_storage_bytes")
    val availableStorageBytes: Long,

    /** Total storage in bytes. */
    @SerialName("total_storage_bytes")
    val totalStorageBytes: Long,

    /** Total RAM in bytes. */
    @SerialName("total_ram_bytes")
    val totalRamBytes: Long,

    /** Available RAM in bytes. */
    @SerialName("available_ram_bytes")
    val availableRamBytes: Long,
)
