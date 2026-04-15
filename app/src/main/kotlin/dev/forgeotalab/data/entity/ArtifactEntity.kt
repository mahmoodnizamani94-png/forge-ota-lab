package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for an extracted artifact (partition image).
 *
 * WHY partitionName is denormalized: The results screen (FR-7) displays
 * artifact cards with partition name, size, checksum, and derivation type.
 * Denormalizing the name avoids a JOIN for every result card render — the
 * partition name is immutable once analyzed.
 *
 * WHY verificationStatus is indexed: FR-7 queries filter artifacts by
 * verification status to separate verified from suspect outputs.
 */
@Entity(
    tableName = "artifacts",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PartitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["partitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("jobId"),
        Index("partitionId"),
        Index("verificationStatus"),
    ],
)
data class ArtifactEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Reference to the extraction job that produced this artifact. */
    val jobId: String,

    /** Reference to the source partition definition. */
    val partitionId: String,

    /** Denormalized partition name for display without JOIN. */
    val partitionName: String,

    /** SAF URI of the exported artifact file. */
    val outputUri: String,

    /** Size of the extracted file in bytes. */
    val sizeBytes: Long,

    /** SHA-256 hash of the extracted file — null before verification. */
    val sha256: String? = null,

    /** Expected SHA-256 from manifest target hash — null for unverifiable. */
    val expectedHash: String? = null,

    /** DerivationType enum name: DIRECT, RECONSTRUCTED, PARTIAL, RAW_UNVERIFIED. */
    val derivationType: String,

    /** Display name of the source package — denormalized for result cards. */
    val sourcePackageDisplayName: String? = null,

    /** VerificationStatus enum name: PENDING, VERIFIED, MISMATCH, UNVERIFIABLE. */
    val verificationStatus: String,

    /** Epoch millis when the artifact was created. */
    val createdAt: Long,

    /** Epoch millis when verification completed — null if not yet verified. */
    val verifiedAt: Long? = null,

    /** JSON array of warning strings for display on the result card. */
    val warnings: String? = null,
)
