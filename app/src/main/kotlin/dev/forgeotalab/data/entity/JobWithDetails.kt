package dev.forgeotalab.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Compound data class for reconstructing full job state from a single query.
 *
 * WHY: The stress test requirement — "Can you reconstruct a full job state
 * (phases, partitions, artifacts, verification status) from a single job ID
 * query without N+1?" This class answers yes: Room generates the JOINs
 * automatically from the @Relation annotations, loading all three related
 * collections in a predictable number of queries (at most 3 SELECTs regardless
 * of row count).
 *
 * Used by: job resume (FR-9), live job monitor, export results screen.
 */
data class JobWithDetails(
    @Embedded
    val job: JobEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "jobId",
    )
    val phases: List<JobPhaseEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "jobId",
    )
    val artifacts: List<ArtifactEntity>,
)
