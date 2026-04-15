package dev.forgeotalab.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Compound data class for loading a package with all its partitions in a
 * single query — prevents the N+1 pattern where displaying a package's
 * partition inventory would otherwise require 1 query for the package
 * followed by N queries for individual partitions.
 *
 * Room generates the JOIN automatically from the @Relation annotation.
 */
data class PackageWithPartitions(
    @Embedded
    val pkg: PackageEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "packageId",
    )
    val partitions: List<PartitionEntity>,
)
