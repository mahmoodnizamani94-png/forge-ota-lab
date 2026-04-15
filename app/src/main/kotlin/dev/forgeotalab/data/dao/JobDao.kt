package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for extraction job records.
 *
 * WHY activeJob query checks both QUEUED and RUNNING: The PRD's concurrent job
 * guard (Non-Negotiable Rule #7) requires blocking a second extraction if any
 * job is either waiting to start or actively running. Checking only RUNNING
 * would allow a race where two jobs are queued simultaneously.
 */
@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: JobEntity)

    @Update
    suspend fun update(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: String): JobEntity?

    /**
     * Concurrent job guard: returns the currently active (QUEUED or RUNNING) job.
     * PRD: Only one extraction job active at a time.
     */
    @Query("SELECT * FROM jobs WHERE status IN ('QUEUED', 'RUNNING') LIMIT 1")
    fun observeActiveJob(): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE status IN ('QUEUED', 'RUNNING') LIMIT 1")
    suspend fun getActiveJob(): JobEntity?

    /**
     * Resumable jobs for restart recovery (FR-9).
     * A job is resumable if it was interrupted (INTERRUPTED) or the process died
     * while it was RUNNING — the latter is detected by WorkManager's retry.
     * PAUSED jobs are also resumable if the user chooses to resume.
     */
    @Query("SELECT * FROM jobs WHERE status IN ('RUNNING', 'PAUSED', 'INTERRUPTED') ORDER BY createdAt DESC")
    fun observeResumableJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('RUNNING', 'PAUSED', 'INTERRUPTED') ORDER BY createdAt DESC")
    suspend fun getResumableJobs(): List<JobEntity>

    /**
     * Interrupted jobs — one-shot query for startup recovery scan.
     * Only returns INTERRUPTED (confirmed process death), not RUNNING
     * (which may be actively executing in another worker).
     */
    @Query("SELECT * FROM jobs WHERE status = 'INTERRUPTED' ORDER BY createdAt DESC")
    suspend fun getInterruptedJobs(): List<JobEntity>

    /**
     * Full job state reconstruction without N+1 — loads phases and artifacts.
     */
    @Transaction
    @Query("SELECT * FROM jobs WHERE id = :jobId")
    fun observeJobWithDetails(jobId: String): Flow<JobWithDetails?>

    @Transaction
    @Query("SELECT * FROM jobs WHERE id = :jobId")
    suspend fun getJobWithDetails(jobId: String): JobWithDetails?

    /**
     * Jobs for a specific package — used when re-opening a package from history.
     */
    @Query("SELECT * FROM jobs WHERE packageId = :packageId ORDER BY createdAt DESC")
    fun observeJobsByPackageId(packageId: String): Flow<List<JobEntity>>

    /**
     * Latest job for a package — for history card status display.
     */
    @Query("SELECT * FROM jobs WHERE packageId = :packageId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestJobForPackage(packageId: String): JobEntity?

    /**
     * Targeted status update — avoids loading and persisting the full entity.
     */
    @Query("UPDATE jobs SET status = :status, completedAt = :completedAt WHERE id = :jobId")
    suspend fun updateStatus(jobId: String, status: String, completedAt: Long? = null)

    /**
     * Checkpoint write — atomic update of resume state (FR-9).
     * WHY separate from updateStatus: checkpoint writes happen per-partition
     * during extraction and must be as lightweight as possible (≤ 5% overhead).
     */
    @Query(
        """
        UPDATE jobs SET 
            lastCheckpointPartitionId = :partitionId, 
            lastCheckpointPhase = :phase,
            completedPartitions = :completedPartitions
        WHERE id = :jobId
        """
    )
    suspend fun updateCheckpoint(
        jobId: String,
        partitionId: String,
        phase: String,
        completedPartitions: Int,
    )

    /**
     * Increment failed partition count — called when a partition fails extraction.
     */
    @Query("UPDATE jobs SET failedPartitions = failedPartitions + 1 WHERE id = :jobId")
    suspend fun incrementFailedPartitions(jobId: String)

    /**
     * Mark a stale RUNNING job as INTERRUPTED — called on app startup when
     * a job is found in RUNNING state but no worker is actively executing it.
     *
     * WHY targeted update: This fires during startup path and must not block
     * UI rendering. A single UPDATE is faster than load-modify-save round-trip.
     */
    @Query(
        """
        UPDATE jobs SET 
            status = 'INTERRUPTED',
            interruptedAt = :interruptedAt
        WHERE id = :jobId AND status = 'RUNNING'
        """
    )
    suspend fun markInterrupted(jobId: String, interruptedAt: Long)

    /**
     * Mark all RUNNING jobs as INTERRUPTED — bulk operation for startup recovery.
     * Returns the number of jobs affected.
     */
    @Query(
        """
        UPDATE jobs SET 
            status = 'INTERRUPTED',
            interruptedAt = :interruptedAt
        WHERE status = 'RUNNING'
        """
    )
    suspend fun markAllRunningAsInterrupted(interruptedAt: Long): Int

    /**
     * Mark a job as resuming — updates status and resume tracking fields.
     * Called by ResumeExtractionUseCase before re-enqueuing the worker.
     */
    @Query(
        """
        UPDATE jobs SET 
            status = 'RUNNING',
            lastResumedAt = :resumedAt,
            resumeCount = resumeCount + 1
        WHERE id = :jobId
        """
    )
    suspend fun markResuming(jobId: String, resumedAt: Long)

    /**
     * Delete a job and all cascading child records (phases, artifacts).
     */
    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteById(jobId: String)
}
