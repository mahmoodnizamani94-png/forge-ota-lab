package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking progress of individual phases within an extraction job.
 *
 * WHY a separate entity: Phase-level tracking enables the PRD's per-phase
 * progress display (Workflow A step 9) and per-partition resume from the last
 * completed phase (FR-9). Without this, the UI cannot show "scan → validate →
 * extract → verify → export" progression per partition.
 */
@Entity(
    tableName = "job_phases",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("jobId"),
    ],
)
data class JobPhaseEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Reference to the parent extraction job. */
    val jobId: String,

    /** JobPhaseType enum name (SCAN, VALIDATE, EXTRACT, etc.). */
    val phase: String,

    /** PhaseStatus enum name (PENDING, RUNNING, COMPLETED, FAILED, SKIPPED). */
    val status: String,

    /** Partition ID if this phase is partition-scoped — null for job-level phases. */
    val partitionId: String? = null,

    /** Epoch millis when this phase began execution. */
    val startedAt: Long? = null,

    /** Epoch millis when this phase reached a terminal status. */
    val completedAt: Long? = null,

    /** Extraction progress as 0–100 percentage for progress bar display. */
    val progressPercent: Int = 0,

    /** Total bytes processed so far in this phase — for throughput calculation. */
    val bytesProcessed: Long = 0L,

    /** Serialized error context if the phase failed — includes diagnostic detail. */
    val errorDetails: String? = null,
)
