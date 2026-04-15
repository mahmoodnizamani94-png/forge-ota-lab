package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for an imported OTA package.
 *
 * WHY indices on lastOpenedAt, supportTier, adapterId: History queries
 * (FR-11) ORDER BY lastOpenedAt DESC. Analysis screens filter by supportTier.
 * Adapter registry lookups JOIN on adapterId. Without these indices, history
 * listing degrades to full table scans in O(n) on every app launch.
 *
 * WHY enums stored as strings: Ordinal-based storage breaks silently if enum
 * variants are reordered or inserted between versions. String storage is
 * forward-compatible and human-readable in database inspector.
 */
@Entity(
    tableName = "packages",
    indices = [
        Index("lastOpenedAt"),
        Index("supportTier"),
        Index("adapterId"),
    ],
)
data class PackageEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** SAF persistable URI obtained via takePersistableUriPermission. */
    val sourceUri: String,

    /** User-visible filename for display in history and analysis screens. */
    val displayName: String,

    /** Total file size in bytes — used for truncation detection and display. */
    val fileSizeBytes: Long,

    /** Detected family name (e.g., "google_pixel", "standard_payload"). */
    val packageFamily: String,

    /** PackageClassification enum name. */
    val classification: String,

    /** SupportTier enum name — set exclusively by Rust core adapter output. */
    val supportTier: String,

    /** True if the package contains incremental (delta) operations. */
    val isIncremental: Boolean,

    /** SlotModel enum name — A/B, A-only, Virtual A/B, or unknown. */
    val slotModel: String,

    /** Target build fingerprint extracted from manifest metadata. */
    val targetFingerprint: String? = null,

    /** Source build fingerprint — present only for incremental packages. */
    val sourceFingerprint: String? = null,

    /** Android security patch level from manifest (e.g., "2026-04-05"). */
    val securityPatchLevel: String? = null,

    /** Device model from manifest metadata (e.g., "oriole", "raven"). */
    val deviceModel: String? = null,

    /** Adapter that classified this package — null for unrecognized packages. */
    val adapterId: String? = null,

    /** Adapter version at time of classification — frozen snapshot. */
    val adapterVersion: String? = null,

    /** Manifest size in bytes — null if manifest not parsed. */
    val manifestSizeBytes: Long? = null,

    /** Total payload size in bytes — null if not a payload-based package. */
    val payloadSizeBytes: Long? = null,

    /** Epoch millis when the package was first imported. */
    val importedAt: Long,

    /** Epoch millis when the package was last opened — updated on re-open. */
    val lastOpenedAt: Long,

    /** True once analysis has successfully completed for this package. */
    val analysisComplete: Boolean = false,

    /** First N magic bytes detected as hex string — useful for Forensic diagnostics. */
    val detectedMagicBytes: String? = null,
)
