package dev.forgeotalab.domain

import android.util.Log
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.data.repository.getOrNull
import javax.inject.Inject

/**
 * Handles cleanup of interrupted jobs when recovery state is corrupted (FR-9).
 *
 * PRD Failure Taxonomy Row #12: "Recovery checkpoint corruption —
 * Do not auto-resume. Offer safe cleanup. Preserve any verified outputs."
 *
 * This use case:
 * 1. Loads the full job state including artifacts
 * 2. Identifies verified artifacts to preserve
 * 3. Cleans temp files via WorkspaceManager
 * 4. Marks the job as FAILED with descriptive error summary
 * 5. Returns the list of preserved artifacts for the user
 *
 * WHY not auto-resume: If the checkpoint is corrupted, we cannot determine
 * which partition to resume from. Auto-resuming from an incorrect point
 * would produce corrupt output, violating PRD Rule #3 (no success before
 * verification) and potentially wasting the user's time.
 */
class CleanupInterruptedJobUseCase @Inject constructor(
    private val jobRepository: JobRepository,
    private val workspaceManager: WorkspaceManager,
) {

    companion object {
        private const val TAG = "CleanupInterruptedJob"
    }

    /**
     * Clean up an interrupted job, preserving verified outputs.
     *
     * @param jobId UUID of the interrupted job to clean up.
     * @return CleanupResult with preserved artifact details.
     */
    suspend fun execute(jobId: String): CleanupResult {
        // Step 1: Load full job state
        val jobDetails = jobRepository.getFullJobState(jobId).getOrNull()
            ?: return CleanupResult.JobNotFound(jobId)

        val job = jobDetails.job

        // Step 2: Identify verified artifacts to preserve
        val verifiedArtifacts = jobDetails.artifacts.filter { artifact ->
            artifact.verificationStatus == VerificationStatus.VERIFIED.name ||
                artifact.verificationStatus == VerificationStatus.UNVERIFIABLE.name
        }

        val preservedUris = verifiedArtifacts.map { it.outputUri }
        val preservedNames = verifiedArtifacts.map { it.partitionName }

        Log.i(
            TAG,
            "Cleaning up job $jobId: preserving ${verifiedArtifacts.size} verified artifacts, " +
                "cleaning temp files",
        )

        // Step 3: Clean temp files — verified output lives in SAF destination
        workspaceManager.cleanupJobWorkspace(jobId)

        // Step 4: Mark job as FAILED with descriptive summary
        val errorSummary = buildString {
            append("Job interrupted and cleaned up. ")
            if (verifiedArtifacts.isNotEmpty()) {
                append("${verifiedArtifacts.size} verified output(s) preserved: ")
                append(preservedNames.joinToString(", "))
                append(". ")
            }
            append("Re-import the package and extract again for remaining partitions.")
        }

        // Update job to FAILED status
        val statusResult = jobRepository.updateJobStatus(jobId, JobStatus.FAILED)
        if (statusResult is DataResult.Error) {
            Log.e(TAG, "Failed to update job status: ${statusResult.message}")
        }

        return CleanupResult.Cleaned(
            jobId = jobId,
            preservedArtifactCount = verifiedArtifacts.size,
            preservedPartitionNames = preservedNames,
            preservedOutputUris = preservedUris,
        )
    }
}

/**
 * Sealed result from cleanup attempt.
 */
sealed class CleanupResult {

    /**
     * Job cleaned up. Verified outputs preserved, temp files removed.
     */
    data class Cleaned(
        val jobId: String,
        val preservedArtifactCount: Int,
        val preservedPartitionNames: List<String>,
        val preservedOutputUris: List<String>,
    ) : CleanupResult()

    /** Job not found in database. */
    data class JobNotFound(val jobId: String) : CleanupResult()
}
