package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.forgeotalab.data.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for extracted artifact records.
 */
@Dao
interface ArtifactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artifact: ArtifactEntity)

    @Update
    suspend fun update(artifact: ArtifactEntity)

    /**
     * All artifacts for a job — powers the export results screen (FR-7).
     */
    @Query("SELECT * FROM artifacts WHERE jobId = :jobId ORDER BY partitionName ASC")
    fun observeByJobId(jobId: String): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE jobId = :jobId ORDER BY partitionName ASC")
    suspend fun getByJobId(jobId: String): List<ArtifactEntity>

    /**
     * Only verified artifacts — used to determine success count and for
     * filesystem browser eligibility checks.
     */
    @Query(
        """
        SELECT * FROM artifacts 
        WHERE jobId = :jobId AND verificationStatus = 'VERIFIED' 
        ORDER BY partitionName ASC
        """
    )
    fun observeVerifiedByJobId(jobId: String): Flow<List<ArtifactEntity>>

    /**
     * Artifacts filtered by verification status — for result filtering UI.
     */
    @Query("SELECT * FROM artifacts WHERE verificationStatus = :status ORDER BY createdAt DESC")
    fun observeByVerificationStatus(status: String): Flow<List<ArtifactEntity>>

    /**
     * Targeted verification update — called after SHA-256 check completes.
     * PRD Rule #3: verification status must be set before any success display.
     */
    @Query(
        """
        UPDATE artifacts SET 
            verificationStatus = :status, 
            sha256 = :sha256, 
            verifiedAt = :verifiedAt 
        WHERE id = :artifactId
        """
    )
    suspend fun updateVerification(
        artifactId: String,
        status: String,
        sha256: String?,
        verifiedAt: Long,
    )

    /**
     * Lookup artifact by partition within a job — for re-extraction targeting.
     */
    @Query("SELECT * FROM artifacts WHERE jobId = :jobId AND partitionId = :partitionId")
    suspend fun getByJobIdAndPartitionId(jobId: String, partitionId: String): ArtifactEntity?

    /**
     * Single artifact lookup — for re-extract and share actions.
     */
    @Query("SELECT * FROM artifacts WHERE id = :artifactId")
    suspend fun getById(artifactId: String): ArtifactEntity?

    /**
     * Artifacts sorted for the result screen: mismatches first, then failed (pending),
     * then unverifiable, then verified. This ensures problems are visible at the top.
     *
     * WHY: PRD stress test — "What does the result screen look like when 12 of 14
     * succeed and 2 fail?" The dominant visual must surface problems, not bury them.
     */
    @Query(
        """
        SELECT * FROM artifacts WHERE jobId = :jobId 
        ORDER BY 
            CASE verificationStatus
                WHEN 'MISMATCH' THEN 0
                WHEN 'PENDING' THEN 1
                WHEN 'UNVERIFIABLE' THEN 2
                WHEN 'VERIFIED' THEN 3
                ELSE 4
            END,
            partitionName ASC
        """
    )
    fun observeByJobIdSorted(jobId: String): Flow<List<ArtifactEntity>>
}
