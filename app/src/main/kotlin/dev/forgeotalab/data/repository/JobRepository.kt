package dev.forgeotalab.data.repository

import dev.forgeotalab.contracts.model.JobPhaseType
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for extraction job persistence.
 *
 * WHY domain enums in the interface signature: Repository methods accept
 * domain types (JobStatus, JobPhaseType) and handle the string conversion
 * internally. Consumers work with type-safe enums; the persistence detail
 * of string storage is hidden.
 */
interface JobRepository {

    /**
     * Observe the currently active job — for concurrent job guard.
     * PRD: Only one extraction job active at a time.
     */
    fun observeActiveJob(): Flow<JobEntity?>

    /**
     * Observe full job state for the live job monitor and results screen.
     * Includes phases and artifacts — N+1-free.
     */
    fun observeJobWithDetails(jobId: String): Flow<JobWithDetails?>

    /**
     * Observe jobs that can be resumed after interruption (FR-9).
     * Includes RUNNING, PAUSED, and INTERRUPTED statuses.
     */
    fun observeResumableJobs(): Flow<List<JobEntity>>

    /**
     * Create a new extraction job. Fails if a job is already active.
     */
    suspend fun createJob(job: JobEntity): DataResult<Unit>

    /**
     * Update job lifecycle status with optional completion timestamp.
     */
    suspend fun updateJobStatus(jobId: String, status: JobStatus): DataResult<Unit>

    /**
     * Write a checkpoint after completing a partition phase (FR-9).
     * Must add ≤ 5% overhead to extraction throughput.
     */
    suspend fun writeCheckpoint(
        jobId: String,
        partitionId: String,
        phase: JobPhaseType,
        completedPartitions: Int,
    ): DataResult<Unit>

    /**
     * One-shot full state reconstruction — for resume logic.
     */
    suspend fun getFullJobState(jobId: String): DataResult<JobWithDetails?>

    /**
     * Check if any job is currently active — for preflight guard.
     */
    suspend fun hasActiveJob(): DataResult<Boolean>

    /**
     * Mark a stale RUNNING job as INTERRUPTED (FR-9).
     * Called on app startup when the process was killed during extraction.
     */
    suspend fun markInterrupted(jobId: String): DataResult<Unit>

    /**
     * Mark all stale RUNNING jobs as INTERRUPTED — bulk startup recovery.
     * Returns the number of jobs affected.
     */
    suspend fun markAllRunningAsInterrupted(): DataResult<Int>

    /**
     * Mark a job as resuming — increments resume count and updates timestamp.
     */
    suspend fun markResuming(jobId: String): DataResult<Unit>

    /**
     * One-shot query for interrupted jobs — for startup recovery scan.
     */
    suspend fun getInterruptedJobs(): DataResult<List<JobEntity>>

    /**
     * One-shot query for all resumable jobs.
     */
    suspend fun getResumableJobs(): DataResult<List<JobEntity>>

    /**
     * Latest job for a specific package — for history card status display.
     */
    suspend fun getLatestJobForPackage(packageId: String): DataResult<JobEntity?>

    /**
     * Delete a job and all cascading children — for history cleanup.
     */
    suspend fun deleteJob(jobId: String): DataResult<Unit>
}
