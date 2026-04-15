package dev.forgeotalab.data.repository.impl

import dev.forgeotalab.contracts.model.JobPhaseType
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.data.dao.JobDao
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobWithDetails
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [JobRepository].
 *
 * WHY createJob checks for active job: The concurrent job guard (PRD Rule #7)
 * must be enforced at the repository level — not just the UI — to prevent
 * race conditions where two creation requests arrive simultaneously.
 */
@Singleton
class JobRepositoryImpl @Inject constructor(
    private val jobDao: JobDao,
) : JobRepository {

    override fun observeActiveJob(): Flow<JobEntity?> {
        return jobDao.observeActiveJob()
    }

    override fun observeJobWithDetails(jobId: String): Flow<JobWithDetails?> {
        return jobDao.observeJobWithDetails(jobId)
    }

    override fun observeResumableJobs(): Flow<List<JobEntity>> {
        return jobDao.observeResumableJobs()
    }

    override suspend fun createJob(job: JobEntity): DataResult<Unit> {
        return try {
            // Enforce concurrent job limit at the data layer.
            val activeJob = jobDao.getActiveJob()
            if (activeJob != null) {
                return DataResult.Error(
                    message = "An extraction is already running (job ${activeJob.id}). " +
                        "Complete or cancel it before starting another.",
                )
            }
            jobDao.insert(job)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to create extraction job: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun updateJobStatus(jobId: String, status: JobStatus): DataResult<Unit> {
        return try {
            val completedAt = when (status) {
                JobStatus.COMPLETED,
                JobStatus.FAILED,
                JobStatus.CANCELED,
                JobStatus.PARTIAL_SUCCESS -> System.currentTimeMillis()
                else -> null
            }
            jobDao.updateStatus(jobId, status.name, completedAt)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to update job $jobId status to $status: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun writeCheckpoint(
        jobId: String,
        partitionId: String,
        phase: JobPhaseType,
        completedPartitions: Int,
    ): DataResult<Unit> {
        return try {
            jobDao.updateCheckpoint(jobId, partitionId, phase.name, completedPartitions)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to write checkpoint for job $jobId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getFullJobState(jobId: String): DataResult<JobWithDetails?> {
        return try {
            DataResult.Success(jobDao.getJobWithDetails(jobId))
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load job state for $jobId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun hasActiveJob(): DataResult<Boolean> {
        return try {
            DataResult.Success(jobDao.getActiveJob() != null)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to check for active job: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun markInterrupted(jobId: String): DataResult<Unit> {
        return try {
            jobDao.markInterrupted(jobId, System.currentTimeMillis())
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to mark job $jobId as interrupted: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun markAllRunningAsInterrupted(): DataResult<Int> {
        return try {
            val count = jobDao.markAllRunningAsInterrupted(System.currentTimeMillis())
            DataResult.Success(count)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to mark running jobs as interrupted: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun markResuming(jobId: String): DataResult<Unit> {
        return try {
            jobDao.markResuming(jobId, System.currentTimeMillis())
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to mark job $jobId as resuming: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getInterruptedJobs(): DataResult<List<JobEntity>> {
        return try {
            DataResult.Success(jobDao.getInterruptedJobs())
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load interrupted jobs: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getResumableJobs(): DataResult<List<JobEntity>> {
        return try {
            DataResult.Success(jobDao.getResumableJobs())
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load resumable jobs: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getLatestJobForPackage(packageId: String): DataResult<JobEntity?> {
        return try {
            DataResult.Success(jobDao.getLatestJobForPackage(packageId))
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load latest job for package $packageId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun deleteJob(jobId: String): DataResult<Unit> {
        return try {
            jobDao.deleteById(jobId)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to delete job $jobId: ${e.message}",
                cause = e,
            )
        }
    }
}
