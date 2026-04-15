package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.forgeotalab.data.entity.JobPhaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for job phase progress records.
 */
@Dao
interface JobPhaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(phases: List<JobPhaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phase: JobPhaseEntity)

    @Update
    suspend fun update(phase: JobPhaseEntity)

    /**
     * All phases for a job — powers the phase-level progress display.
     */
    @Query("SELECT * FROM job_phases WHERE jobId = :jobId ORDER BY startedAt ASC")
    fun observeByJobId(jobId: String): Flow<List<JobPhaseEntity>>

    @Query("SELECT * FROM job_phases WHERE jobId = :jobId ORDER BY startedAt ASC")
    suspend fun getByJobId(jobId: String): List<JobPhaseEntity>

    /**
     * Targeted progress update — avoids full entity load for streaming progress.
     * Called at most once per second per the PRD's notification update rate limit.
     */
    @Query(
        """
        UPDATE job_phases SET 
            progressPercent = :progressPercent, 
            bytesProcessed = :bytesProcessed 
        WHERE id = :phaseId
        """
    )
    suspend fun updateProgress(phaseId: String, progressPercent: Int, bytesProcessed: Long)

    /**
     * Mark phase as completed — sets terminal status and completion timestamp.
     */
    @Query(
        """
        UPDATE job_phases SET 
            status = :status, 
            completedAt = :completedAt,
            progressPercent = 100
        WHERE id = :phaseId
        """
    )
    suspend fun completePhase(phaseId: String, status: String, completedAt: Long)

    /**
     * Phases for a specific partition within a job — for per-partition resume.
     */
    @Query("SELECT * FROM job_phases WHERE jobId = :jobId AND partitionId = :partitionId ORDER BY startedAt ASC")
    suspend fun getByJobIdAndPartitionId(jobId: String, partitionId: String): List<JobPhaseEntity>

    /**
     * Failed phases for a job — drives partition-level failure details in
     * diagnostics export (FR-10). Only returns phases with status "FAILED".
     */
    @Query("SELECT * FROM job_phases WHERE jobId = :jobId AND status = 'FAILED' ORDER BY startedAt ASC")
    suspend fun getFailedPhasesForJob(jobId: String): List<JobPhaseEntity>
}
