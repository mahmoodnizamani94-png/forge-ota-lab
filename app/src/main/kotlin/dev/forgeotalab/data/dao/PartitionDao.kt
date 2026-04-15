package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.forgeotalab.data.entity.PartitionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for partition records.
 *
 * WHY batch insert: Analysis produces all partitions for a package in a
 * single pass. Inserting them individually would be N round-trips to SQLite;
 * batch insert is a single transaction.
 */
@Dao
interface PartitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(partitions: List<PartitionEntity>)

    /**
     * Observable partition inventory for a package — used by the analysis screen (FR-6).
     */
    @Query("SELECT * FROM partitions WHERE packageId = :packageId ORDER BY name ASC")
    fun observeByPackageId(packageId: String): Flow<List<PartitionEntity>>

    /**
     * One-shot partition list for extraction job preparation.
     */
    @Query("SELECT * FROM partitions WHERE packageId = :packageId ORDER BY name ASC")
    suspend fun getByPackageId(packageId: String): List<PartitionEntity>

    /**
     * Batch lookup by IDs for extraction job creation — resolves selected partition IDs
     * from the JSON list stored on JobEntity.
     */
    @Query("SELECT * FROM partitions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<PartitionEntity>

    /**
     * Extractable partitions only — filtered inventory for selection UI.
     */
    @Query("SELECT * FROM partitions WHERE packageId = :packageId AND isExtractable = 1 ORDER BY name ASC")
    fun observeExtractableByPackageId(packageId: String): Flow<List<PartitionEntity>>

    /**
     * Partitions grouped by category for FR-6 display grouping.
     */
    @Query("SELECT * FROM partitions WHERE packageId = :packageId ORDER BY category ASC, name ASC")
    fun observeByPackageIdGroupedByCategory(packageId: String): Flow<List<PartitionEntity>>

    @Query("SELECT * FROM partitions WHERE id = :id")
    suspend fun getById(id: String): PartitionEntity?
}
