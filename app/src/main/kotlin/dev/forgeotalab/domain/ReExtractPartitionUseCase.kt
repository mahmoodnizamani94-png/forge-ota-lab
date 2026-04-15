package dev.forgeotalab.domain

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.workers.ExtractionWorker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-extracts a single partition that failed verification.
 *
 * WHY single-partition: PRD FR-7 mandates "offer single-partition re-extract"
 * for hash mismatches. Re-extracting the entire job when only one partition
 * failed would waste user time and device resources.
 *
 * Creates a new WorkManager request targeting just the failed partition.
 * The existing artifact record is preserved (audit trail of the failed attempt)
 * and a new artifact is created upon successful re-extraction.
 */
@Singleton
class ReExtractPartitionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artifactRepository: ArtifactRepository,
    private val jobRepository: JobRepository,
) {

    companion object {
        private const val TAG = "ReExtractPartition"
        const val KEY_RE_EXTRACT_PARTITION_ID = "re_extract_partition_id"
        const val KEY_RE_EXTRACT_ARTIFACT_ID = "re_extract_artifact_id"
    }

    /**
     * Re-extract a single partition from its original job.
     *
     * @param artifactId The ID of the failed artifact to re-extract.
     * @return Result indicating whether re-extraction was enqueued.
     */
    suspend fun execute(artifactId: String): ReExtractResult {
        // Load the failed artifact
        val artifactResult = artifactRepository.getArtifactById(artifactId)
        val artifact = when (artifactResult) {
            is DataResult.Success -> artifactResult.data
            is DataResult.Error -> return ReExtractResult.Error(artifactResult.message)
        } ?: return ReExtractResult.Error("Artifact not found")

        // Verify the artifact actually needs re-extraction
        if (artifact.verificationStatus != "MISMATCH") {
            return ReExtractResult.Error(
                "Artifact is ${artifact.verificationStatus}, not MISMATCH. " +
                    "Re-extraction is only offered for verification failures.",
            )
        }

        // Check no active job (PRD: only one extraction job at a time)
        val hasActive = when (val activeResult = jobRepository.hasActiveJob()) {
            is DataResult.Success -> activeResult.data
            is DataResult.Error -> return ReExtractResult.Error(activeResult.message)
        }

        if (hasActive) {
            return ReExtractResult.Error(
                "An extraction is already running. " +
                    "Complete or cancel it before re-extracting.",
            )
        }

        // Enqueue single-partition re-extraction via WorkManager.
        // Uses WorkManager.getInstance(context) to match the pattern
        // established by StartExtractionUseCase and CancelExtractionUseCase.
        val inputData = Data.Builder()
            .putString(ExtractionWorker.KEY_JOB_ID, artifact.jobId)
            .putString(KEY_RE_EXTRACT_PARTITION_ID, artifact.partitionId)
            .putString(KEY_RE_EXTRACT_ARTIFACT_ID, artifactId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        Log.i(
            TAG,
            "Enqueued re-extraction for partition ${artifact.partitionName} " +
                "(artifact=$artifactId, job=${artifact.jobId})",
        )

        return ReExtractResult.Enqueued(
            jobId = artifact.jobId,
            partitionName = artifact.partitionName,
        )
    }
}

/** Result of a single-partition re-extraction request. */
sealed class ReExtractResult {
    data class Enqueued(
        val jobId: String,
        val partitionName: String,
    ) : ReExtractResult()

    data class Error(val message: String) : ReExtractResult()
}
