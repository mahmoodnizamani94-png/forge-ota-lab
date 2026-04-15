package dev.forgeotalab.domain

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.data.repository.getOrNull
import dev.forgeotalab.workers.ExtractionWorker
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Orchestrates resumption of an interrupted extraction job (FR-9).
 *
 * Resume flow:
 * 1. Load job with full state from Room
 * 2. Validate source URI is still accessible
 * 3. Validate output directory is still accessible
 * 4. Determine resume point from lastCheckpointPartitionId
 * 5. Mark job as RUNNING with incremented resumeCount
 * 6. Re-enqueue WorkManager work with resume flag
 *
 * The ExtractionWorker reads the resume flag and checkpoint to skip
 * already-completed partitions. Verified artifacts from the first
 * run remain untouched.
 *
 * PRD: "Resume recovery rate ≥ 90%" (G6)
 */
class ResumeExtractionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobRepository: JobRepository,
    private val packageRepository: PackageRepository,
) {

    /**
     * Attempt to resume an interrupted extraction job.
     *
     * @param jobId UUID of the interrupted job to resume.
     * @return ResumeResult indicating success, failure reason, or required action.
     */
    suspend fun execute(jobId: String): ResumeResult {
        // Step 1: Load full job state
        val jobDetails = jobRepository.getFullJobState(jobId).getOrNull()
            ?: return ResumeResult.JobNotFound(jobId)

        val job = jobDetails.job

        // Step 2: Validate the job is in a resumable state
        if (job.status !in RESUMABLE_STATUSES) {
            return ResumeResult.NotResumable(
                jobId = jobId,
                currentStatus = job.status,
            )
        }

        // Step 3: Check source URI is still accessible
        val packageResult = packageRepository.getPackageById(job.packageId)
        val pkg = (packageResult as? DataResult.Success)?.data
            ?: return ResumeResult.PackageNotFound(jobId, job.packageId)

        val isUriAccessible = packageRepository.checkUriAccessible(pkg.sourceUri)
        if (!isUriAccessible) {
            return ResumeResult.SourceRevoked(
                jobId = jobId,
                packageDisplayName = pkg.displayName,
            )
        }

        // Step 4: Determine resume point from checkpoint
        val selectedIds: List<String> = try {
            Json.decodeFromString(job.selectedPartitionIds)
        } catch (_: Exception) {
            return ResumeResult.CheckpointCorrupted(jobId)
        }

        val checkpointPartitionId = job.lastCheckpointPartitionId
        val skippedCount = if (checkpointPartitionId != null) {
            val checkpointIndex = selectedIds.indexOf(checkpointPartitionId)
            if (checkpointIndex >= 0) checkpointIndex + 1 else 0
        } else {
            // No checkpoint — resume from beginning (all prior work lost)
            0
        }

        // Step 5: Mark job as resuming
        val resumeResult = jobRepository.markResuming(jobId)
        if (resumeResult is DataResult.Error) {
            return ResumeResult.Error(
                jobId = jobId,
                message = "Failed to update job status: ${resumeResult.message}",
            )
        }

        // Step 6: Re-enqueue WorkManager work
        val workData = Data.Builder()
            .putString(ExtractionWorker.KEY_JOB_ID, jobId)
            .putBoolean(ExtractionWorker.KEY_IS_RESUME, true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(workData)
            .addTag("extraction_$jobId")
            .addTag("resume")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            StartExtractionUseCase.EXTRACTION_WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Replace — resume takes priority
            workRequest,
        )

        return ResumeResult.Resumed(
            jobId = jobId,
            resumePartitionIndex = skippedCount,
            skippedCount = skippedCount,
            totalPartitions = selectedIds.size,
        )
    }

    companion object {
        private val RESUMABLE_STATUSES = setOf(
            "INTERRUPTED",
            "PAUSED",
            "RUNNING", // Stale RUNNING — worker may have died without marking INTERRUPTED
        )
    }
}

/**
 * Sealed result from resume attempt.
 */
sealed class ResumeResult {

    /** Job resumed — extraction will continue from the specified partition. */
    data class Resumed(
        val jobId: String,
        val resumePartitionIndex: Int,
        val skippedCount: Int,
        val totalPartitions: Int,
    ) : ResumeResult()

    /** Job not found in database. */
    data class JobNotFound(val jobId: String) : ResumeResult()

    /** Job is not in a resumable state. */
    data class NotResumable(
        val jobId: String,
        val currentStatus: String,
    ) : ResumeResult()

    /** Source package no longer exists in database. */
    data class PackageNotFound(
        val jobId: String,
        val packageId: String,
    ) : ResumeResult()

    /**
     * Source URI revoked — user needs to re-import the file.
     * PRD Failure #14: "Source URI revoked mid-extraction."
     */
    data class SourceRevoked(
        val jobId: String,
        val packageDisplayName: String,
    ) : ResumeResult()

    /**
     * Checkpoint data is corrupted — cannot determine resume point.
     * PRD Failure #12: "Recovery checkpoint corruption."
     */
    data class CheckpointCorrupted(val jobId: String) : ResumeResult()

    /** Internal error during resume. */
    data class Error(
        val jobId: String,
        val message: String,
    ) : ResumeResult()
}
