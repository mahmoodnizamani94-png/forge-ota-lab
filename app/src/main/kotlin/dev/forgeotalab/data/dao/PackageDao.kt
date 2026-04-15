package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PackageWithJobs
import dev.forgeotalab.data.entity.PackageWithPartitions
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for OTA package records.
 *
 * WHY @Transaction on relationship queries: Room's @Relation queries execute
 * multiple SELECTs. Without @Transaction, a concurrent write between the parent
 * and child queries could produce inconsistent results. @Transaction ensures
 * atomic snapshot reads.
 */
@Dao
interface PackageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pkg: PackageEntity)

    @Update
    suspend fun update(pkg: PackageEntity)

    @Delete
    suspend fun delete(pkg: PackageEntity)

    @Query("SELECT * FROM packages WHERE id = :id")
    fun observeById(id: String): Flow<PackageEntity?>

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getById(id: String): PackageEntity?

    /**
     * History listing ordered by most recently opened first.
     * FR-11: Display as reverse-chronological list, limited to [limit] entries.
     */
    @Query("SELECT * FROM packages ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecentPackages(limit: Int = 100): Flow<List<PackageEntity>>

    /**
     * N+1-free package-with-partitions load for the analysis screen.
     */
    @Transaction
    @Query("SELECT * FROM packages WHERE id = :packageId")
    fun observePackageWithPartitions(packageId: String): Flow<PackageWithPartitions?>

    /**
     * N+1-free package-with-partitions load for one-shot queries.
     */
    @Transaction
    @Query("SELECT * FROM packages WHERE id = :packageId")
    suspend fun getPackageWithPartitions(packageId: String): PackageWithPartitions?

    /**
     * Package with its jobs for history re-open (FR-11).
     */
    @Transaction
    @Query("SELECT * FROM packages WHERE id = :packageId")
    fun observePackageWithJobs(packageId: String): Flow<PackageWithJobs?>

    /**
     * 90-day auto-purge: delete packages older than [cutoffEpochMs].
     * FR-11: Entries auto-purge after 90 days.
     */
    @Query("DELETE FROM packages WHERE lastOpenedAt < :cutoffEpochMs")
    suspend fun purgeOlderThan(cutoffEpochMs: Long): Int

    /**
     * Total package count for 100-entry history limit enforcement.
     */
    @Query("SELECT COUNT(*) FROM packages")
    fun observePackageCount(): Flow<Int>

    /**
     * Targeted update for history touch — only updates the timestamp column.
     */
    @Query("UPDATE packages SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpenedAt(id: String, timestamp: Long)

    /**
     * Delete a package by ID — for swipe-to-delete in history (FR-11).
     * Cascade deletes remove associated jobs, phases, artifacts, and partitions.
     */
    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Purge excess entries beyond the history limit (FR-11: last 100 entries).
     * Deletes the oldest entries beyond [keepCount] ordered by lastOpenedAt.
     *
     * WHY subquery: SQLite doesn't support LIMIT in DELETE directly.
     * The subquery finds IDs to keep, and deletion targets everything else.
     */
    @Query(
        """
        DELETE FROM packages WHERE id NOT IN (
            SELECT id FROM packages ORDER BY lastOpenedAt DESC LIMIT :keepCount
        )
        """
    )
    suspend fun purgeExcessEntries(keepCount: Int): Int

    /**
     * Get IDs of packages that expired beyond the retention cutoff.
     * Used by the periodic purge worker for structured event logging.
     */
    @Query("SELECT id FROM packages WHERE lastOpenedAt < :cutoffEpochMs")
    suspend fun getExpiredPackageIds(cutoffEpochMs: Long): List<String>

    /**
     * One-shot total package count for limit enforcement decisions.
     */
    @Query("SELECT COUNT(*) FROM packages")
    suspend fun getPackageCount(): Int
}
