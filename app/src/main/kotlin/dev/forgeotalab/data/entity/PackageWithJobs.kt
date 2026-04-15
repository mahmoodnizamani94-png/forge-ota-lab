package dev.forgeotalab.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Compound data class for loading a package with all its jobs.
 *
 * WHY: History re-open (FR-11) needs to know whether a package has pending,
 * active, or completed jobs to show the appropriate badge and destination screen.
 * Loading this in a single query prevents N+1 when rendering the history list.
 */
data class PackageWithJobs(
    @Embedded
    val pkg: PackageEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "packageId",
    )
    val jobs: List<JobEntity>,
)
