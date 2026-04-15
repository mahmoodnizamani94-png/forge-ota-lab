package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for cached base images used in incremental OTA validation.
 *
 * WHY a separate entity from BaseMatchEntity: BaseMatchEntity tracks the
 * relationship between a specific incremental package and its required bases.
 * BaseCacheEntity tracks the physical cached files independent of any package.
 * Multiple packages can reference the same cached base via fingerprint+partition
 * lookup, enabling cross-package cache reuse.
 *
 * WHY LRU via lastUsedAt: The PRD specifies "LRU eviction with configurable
 * storage ceiling." When the cache exceeds the ceiling, the eviction manager
 * deletes entries with the oldest lastUsedAt first. Each successful validation
 * match touches lastUsedAt to prevent frequently-used bases from being evicted.
 *
 * WHY index on fingerprint+partitionName: Cache hit lookups query by these two
 * fields (e.g., "do we have a cached system.img for build fingerprint X?").
 * Without a composite index, every auto-match scan degrades to a full table scan.
 */
@Entity(
    tableName = "base_cache",
    indices = [
        Index("fingerprint", "partitionName"),
        Index("lastUsedAt"),
    ],
)
data class BaseCacheEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Build fingerprint of the cached base image. */
    val fingerprint: String,

    /** Partition name this base serves (e.g., "system", "vendor"). */
    val partitionName: String,

    /** Absolute file path within app cache directory. */
    val cachePath: String,

    /** File size in bytes — used for LRU eviction budget calculation. */
    val sizeBytes: Long,

    /** SHA-256 hash of the cached base image — integrity verification. */
    val sha256: String,

    /** Original SAF URI from which this base was imported — provenance. */
    val sourceUri: String,

    /** Epoch millis when the base was first cached. */
    val cachedAt: Long,

    /** Epoch millis when this base was last used in a validation match.
     * Updated on each successful match to keep frequently-used bases fresh. */
    val lastUsedAt: Long,
)
