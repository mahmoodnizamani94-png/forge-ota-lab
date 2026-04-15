package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for an extraction job.
 *
 * WHY checkpoint fields on the job itself: FR-9 requires per-partition resume.
 * Storing the last completed partition and phase directly on the job avoids
 * an extra query to reconstruct resume state. The checkpoint is written
 * atomically with phase completion to survive process death.
 *
 * WHY selectedPartitionIds is JSON: A junction table would be more normalized,
 * but partition selection is ephemeral (tied to a single job), the list is
 * small (typically 3–20 entries), and the IDs are only looked up at job start
 * and resume — not queried or filtered independently.
 */
@Entity(
    tableName = "jobs",
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
        Index("status"),
        Index("createdAt"),
    ],
)
data class JobEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Reference to the source package. */
    val packageId: String,

    /** JobStatus enum name — current lifecycle state. */
    val status: String,

    /** Frozen snapshot of the support tier when the job was created. */
    val supportTierAtCreation: String,

    /** JSON array of partition IDs selected for extraction. */
    val selectedPartitionIds: String,

    /** SAF URI of the user-selected output directory for exports. */
    val outputDirectoryUri: String? = null,

    /** Epoch millis when the job was created. */
    val createdAt: Long,

    /** Epoch millis when extraction actually started — null if still queued. */
    val startedAt: Long? = null,

    /** Epoch millis when the job reached a terminal state. */
    val completedAt: Long? = null,

    /** Total number of partitions selected for extraction. */
    val totalPartitions: Int,

    /** Number of partitions successfully extracted and verified. */
    val completedPartitions: Int = 0,

    /** Number of partitions that failed extraction or verification. */
    val failedPartitions: Int = 0,

    /** Last fully completed partition ID — resume marker for FR-9. */
    val lastCheckpointPartitionId: String? = null,

    /** JobPhaseType at last checkpoint — resume starts from next phase. */
    val lastCheckpointPhase: String? = null,

    /**
     * Epoch millis when interruption was detected — for resume latency tracking (G6).
     * Set when a stale RUNNING job is marked as INTERRUPTED on next app launch.
     */
    val interruptedAt: Long? = null,

    /**
     * Number of times this job has been resumed after interruption.
     * Tracks G6 (resume recovery rate) reliability metrics.
     */
    val resumeCount: Int = 0,

    /**
     * Epoch millis when the most recent resume started.
     * Together with interruptedAt, measures resume latency.
     */
    val lastResumedAt: Long? = null,

    /** Serialized error summary for failed jobs — used in diagnostics export. */
    val errorSummary: String? = null,

    /** WorkManager UUID for cancellation and status tracking. */
    val workManagerRequestId: String? = null,
)
