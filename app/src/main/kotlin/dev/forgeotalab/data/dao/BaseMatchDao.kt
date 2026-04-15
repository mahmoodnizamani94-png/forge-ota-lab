package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.forgeotalab.data.entity.BaseMatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for incremental OTA base match records.
 *
 * WHY observable queries: The prerequisite wizard (FR-4) needs to reactively
 * update as the user imports and validates base images. Flow-based queries
 * ensure the UI reflects validation results immediately.
 */
@Dao
interface BaseMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<BaseMatchEntity>)

    @Update
    suspend fun update(match: BaseMatchEntity)

    /**
     * All base match requirements for a package — powers the prerequisite wizard.
     */
    @Query("SELECT * FROM base_matches WHERE packageId = :packageId ORDER BY partitionName ASC")
    fun observeByPackageId(packageId: String): Flow<List<BaseMatchEntity>>

    @Query("SELECT * FROM base_matches WHERE packageId = :packageId ORDER BY partitionName ASC")
    suspend fun getByPackageId(packageId: String): List<BaseMatchEntity>

    /**
     * Only unresolved prerequisites — determines if extraction CTA should be enabled.
     * FR-4: Extraction CTA remains disabled until all selected partitions validate.
     */
    @Query(
        """
        SELECT * FROM base_matches 
        WHERE packageId = :packageId AND matchStatus != 'MATCHED' 
        ORDER BY partitionName ASC
        """
    )
    fun observeUnmatchedByPackageId(packageId: String): Flow<List<BaseMatchEntity>>

    /**
     * Count of unresolved prerequisites — for quick CTA enable/disable check.
     */
    @Query(
        """
        SELECT COUNT(*) FROM base_matches 
        WHERE packageId = :packageId AND matchStatus != 'MATCHED'
        """
    )
    suspend fun getUnmatchedCount(packageId: String): Int

    /**
     * Update a single base match after validation attempt.
     */
    @Query(
        """
        UPDATE base_matches SET 
            matchedBaseUri = :baseUri,
            matchStatus = :status,
            mismatchField = :mismatchField,
            mismatchExpected = :mismatchExpected,
            mismatchActual = :mismatchActual,
            validatedAt = :validatedAt
        WHERE id = :matchId
        """
    )
    suspend fun updateMatchResult(
        matchId: String,
        baseUri: String?,
        status: String,
        mismatchField: String?,
        mismatchExpected: String?,
        mismatchActual: String?,
        validatedAt: Long,
    )

    /**
     * Lookup a single base match by ID — used by the validation pipeline.
     */
    @Query("SELECT * FROM base_matches WHERE id = :matchId")
    suspend fun getById(matchId: String): BaseMatchEntity?

    /**
     * Update the raw export allowed flag for advanced mode.
     * FR-5: "unsafe raw export allowed per partition when user explicitly opts in."
     */
    @Query("UPDATE base_matches SET rawExportAllowed = :allowed WHERE id = :matchId")
    suspend fun updateRawExportAllowed(matchId: String, allowed: Boolean)

    /**
     * Update the baseCacheId reference when a match comes from cache.
     */
    @Query("UPDATE base_matches SET baseCacheId = :cacheId WHERE id = :matchId")
    suspend fun updateBaseCacheId(matchId: String, cacheId: String)
}
