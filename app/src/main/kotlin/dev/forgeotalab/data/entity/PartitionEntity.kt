package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a partition within an OTA package.
 *
 * WHY CASCADE on delete: When a package is removed from history (FR-11 purge
 * after 90 days or 100-entry limit), all its partitions should be cleaned up
 * without requiring explicit deletion queries.
 *
 * WHY index on packageId: Every partition query filters by package. Without
 * this index, partition inventory display degrades to a table scan.
 */
@Entity(
    tableName = "partitions",
    foreignKeys = [
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("packageId"),
        Index("category"),
    ],
)
data class PartitionEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Reference to the parent package. */
    val packageId: String,

    /** Partition name as declared in the manifest (e.g., "boot", "system"). */
    val name: String,

    /** PartitionCategory enum name for FR-6 grouping. */
    val category: String,

    /** Compressed size in bytes within the payload. */
    val sizeBytes: Long,

    /** Estimated decompressed output size for storage budgeting (FR-6). */
    val estimatedExtractedSizeBytes: Long,

    /** Number of InstallOperations for this partition. */
    val operationCount: Int,

    /** Compression algorithm: "XZ", "BZ2", "ZSTD", "RAW", or null. */
    val compressAlgorithm: String? = null,

    /** Whether this partition can be extracted with the current adapter. */
    val isExtractable: Boolean,

    /** Human-readable reason if partition is not extractable. */
    val notExtractableReason: String? = null,

    /** Slot suffix if applicable: "_a", "_b", or null. */
    val slotSuffix: String? = null,

    /** Expected source hash for incremental base validation. */
    val sourceHash: String? = null,

    /** Expected SHA-256 of the fully extracted partition image. */
    val targetHash: String? = null,
)
