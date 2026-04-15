package dev.forgeotalab.domain

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import javax.inject.Inject

/**
 * Cancellation flow for an active extraction job.
 *
 * PRD: "Cancellation preserves verified outputs. Clean temporary files
 * not needed for resume. Completed, verified partitions are never deleted
 * by cancellation."
 *
 * Implementation:
 * 1. Set job status to CANCELED in Room DB
 * 2. Cancel WorkManager work by unique name
 * 3. Worker observes isStopped → stops Rust extraction
 * 4. Worker cleans in-progress temp file, preserves completed artifacts
 */
class CancelExtractionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobRepository: JobRepository,
    private val workspaceManager: WorkspaceManager,
) {

    /**
     * Cancel the currently active extraction job.
     *
     * @param jobId UUID of the job to cancel.
     * @return CancelResult indicating success or failure.
     */
    suspend fun execute(jobId: String): CancelResult {
        // Step 1: Update job status in Room
        val statusResult = jobRepository.updateJobStatus(jobId, JobStatus.CANCELED)
        if (statusResult is DataResult.Error) {
            return CancelResult.Error(
                message = "Failed to update job status: ${statusResult.message}",
            )
        }

        // Step 2: Cancel WorkManager work
        // WHY cancelUniqueWork: This cancels the unique work chain, which
        // triggers Worker.onStopped() → sets the Rust cancel token
        WorkManager.getInstance(context)
            .cancelUniqueWork(StartExtractionUseCase.EXTRACTION_WORK_NAME)

        // Step 3: Clean the workspace (temp files only — verified artifacts
        // live in the SAF output directory which we never touch)
        workspaceManager.cleanupJobWorkspace(jobId)

        return CancelResult.Canceled(jobId = jobId)
    }
}

/**
 * Sealed result from cancellation attempt.
 */
sealed class CancelResult {

    /** Job successfully canceled. Verified artifacts preserved. */
    data class Canceled(val jobId: String) : CancelResult()

    /** Cancellation encountered an error. */
    data class Error(val message: String) : CancelResult()
}
