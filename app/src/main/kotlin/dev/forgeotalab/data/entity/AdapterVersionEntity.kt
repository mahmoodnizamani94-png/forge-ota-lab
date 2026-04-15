package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a signed adapter version in the local registry.
 *
 * WHY family is indexed: FR-2 requires adapter lookup by family to complete
 * in ≤ 50 ms. Without this index, lookup degrades with adapter count.
 *
 * WHY isRevoked is tracked: The PRD's revocation mechanism requires immediate
 * removal of revoked adapter versions from the active registry. Soft-delete
 * via this flag preserves the record for diagnostics while preventing use.
 */
@Entity(
    tableName = "adapter_versions",
    indices = [
        Index("family"),
    ],
)
data class AdapterVersionEntity(
    /** Adapter ID from the signed manifest (e.g., "google_pixel_full_v1"). */
    @PrimaryKey
    val id: String,

    /** OTA family this adapter handles (e.g., "google_pixel", "standard_payload"). */
    val family: String,

    /** Semantic version string of the adapter (e.g., "1.0.0"). */
    val version: String,

    /** SupportTier enum name — tier this adapter provides for its family. */
    val supportTier: String,

    /** Whether this adapter version has been revoked via manifest update. */
    val isRevoked: Boolean = false,

    /** Minimum app version required to use this adapter — null if no constraint. */
    val minimumAppVersion: String? = null,

    /** Human-readable compatibility notes from the manifest. */
    val compatibilityNotes: String? = null,

    /** Epoch millis when this adapter was first installed locally. */
    val installedAt: Long,

    /** Epoch millis when the adapter was last verified against the manifest. */
    val lastRefreshedAt: Long,
)
