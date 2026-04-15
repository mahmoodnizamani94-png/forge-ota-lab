package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.forgeotalab.data.entity.AdapterVersionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the local adapter version registry.
 *
 * WHY insertOrReplace: Manifest refresh delivers the full adapter set.
 * Using REPLACE strategy performs an upsert — new adapters are inserted,
 * existing ones are updated in a single operation.
 */
@Dao
interface AdapterVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(adapter: AdapterVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(adapters: List<AdapterVersionEntity>)

    /**
     * Full registry — observable for settings/diagnostics display.
     */
    @Query("SELECT * FROM adapter_versions ORDER BY family ASC, version DESC")
    fun observeAll(): Flow<List<AdapterVersionEntity>>

    /**
     * Family lookup for classification — must complete in ≤ 50ms (FR-2).
     * Indexed on `family` column to meet this constraint.
     */
    @Query("SELECT * FROM adapter_versions WHERE family = :family AND isRevoked = 0 LIMIT 1")
    suspend fun getByFamily(family: String): AdapterVersionEntity?

    /**
     * Adapter lookup by ID — for diagnostics and version pinning.
     */
    @Query("SELECT * FROM adapter_versions WHERE id = :adapterId")
    suspend fun getById(adapterId: String): AdapterVersionEntity?

    /**
     * Mark an adapter version as revoked — immediate removal from active registry.
     */
    @Query("UPDATE adapter_versions SET isRevoked = 1 WHERE id = :adapterId")
    suspend fun markRevoked(adapterId: String)

    /**
     * Active (non-revoked) adapters for the registry display.
     */
    @Query("SELECT * FROM adapter_versions WHERE isRevoked = 0 ORDER BY family ASC")
    fun observeActiveAdapters(): Flow<List<AdapterVersionEntity>>

    /**
     * Update refresh timestamp — called after successful manifest verification.
     */
    @Query("UPDATE adapter_versions SET lastRefreshedAt = :timestamp WHERE id = :adapterId")
    suspend fun updateRefreshTimestamp(adapterId: String, timestamp: Long)
}
