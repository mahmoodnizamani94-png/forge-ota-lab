package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.forgeotalab.data.entity.BaseCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the base image LRU cache.
 *
 * WHY observable queries: The wizard's cache summary ("3 of 5 bases from cache")
 * and the cache management section need reactive updates when bases are cached,
 * evicted, or manually deleted.
 *
 * WHY LRU via lastUsedAt ordering: The PRD specifies LRU eviction. Querying
 * by ascending lastUsedAt gives us the eviction order directly from SQLite
 * without in-memory sorting.
 */
@Dao
interface BaseCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: BaseCacheEntity)

    /**
     * All cached bases — powers the cache management UI.
     * Ordered by most recently used first for display.
     */
    @Query("SELECT * FROM base_cache ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<BaseCacheEntity>>

    @Query("SELECT * FROM base_cache ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<BaseCacheEntity>

    /**
     * Cache hit lookup — find a cached base matching fingerprint + partition.
     *
     * WHY both fields: A base image with the right fingerprint but wrong
     * partition name is useless (stress test scenario from the user request).
     * Both must match for a valid cache hit.
     */
    @Query(
        """
        SELECT * FROM base_cache 
        WHERE fingerprint = :fingerprint AND partitionName = :partitionName 
        LIMIT 1
        """
    )
    suspend fun findByFingerprintAndPartition(
        fingerprint: String,
        partitionName: String,
    ): BaseCacheEntity?

    /**
     * Total cache storage consumption — for budget display and eviction decisions.
     */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM base_cache")
    suspend fun getTotalCacheSize(): Long

    /**
     * Oldest entries by LRU order — eviction candidates.
     */
    @Query("SELECT * FROM base_cache ORDER BY lastUsedAt ASC LIMIT :count")
    suspend fun getOldestEntries(count: Int): List<BaseCacheEntity>

    /**
     * Delete a single cache entry — manual cleanup from management UI.
     */
    @Query("DELETE FROM base_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clear entire cache — "Clear All" action in cache management.
     */
    @Query("DELETE FROM base_cache")
    suspend fun deleteAll()

    /**
     * Touch lastUsedAt to refresh LRU position on successful match.
     */
    @Query("UPDATE base_cache SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun touchLastUsed(id: String, timestamp: Long)

    /**
     * Count of cached entries — for summary display.
     */
    @Query("SELECT COUNT(*) FROM base_cache")
    suspend fun getCount(): Int
}
