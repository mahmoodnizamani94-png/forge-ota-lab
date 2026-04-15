package dev.forgeotalab.domain

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.contracts.model.ExtractionRequest
import dev.forgeotalab.contracts.model.JobPhaseType
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.PhaseStatus
import dev.forgeotalab.data.dao.PartitionDao
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobPhaseEntity
import dev.forgeotalab.data.dao.JobPhaseDao
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.workers.ExtractionWorker
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Orchestrates extraction job creation and WorkManager enqueue.
 *
 * WHY a use case: The extraction start flow touches multiple layers:
 * preflight validation → Room persistence → WorkManager enqueueing.
 * Centralizing this prevents the ViewModel from knowing about workers.
 *
 * Concurrent job guard is enforced at TWO levels:
 * 1. JobRepository.createJob() checks the DB (atomic)
 * 2. WorkManager ExistingWorkPolicy.KEEP prevents duplicate work
 */
class StartExtractionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preflightValidator: PreflightValidator,
    private val jobRepository: JobRepository,
    private val partitionDao: PartitionDao,
    private val jobPhaseDao: JobPhaseDao,
) {

    companion object {
        /** Unique work name — ensures only one extraction job runs at a time. */
        const val EXTRACTION_WORK_NAME = "forge_extraction_job"
    }

    /**
     * Start an extraction job.
     *
     * @param request Extraction parameters from the UI.
     * @return StartExtractionResult — success with jobId, or failure details.
     */
    suspend fun execute(request: ExtractionRequest): StartExtractionResult {
        // Step 1: Load selected partitions for preflight
        val partitions = partitionDao.getByPackageId(request.packageId)
        val selectedPartitions = partitions.filter { it.id in request.selectedPartitionIds }

        if (selectedPartitions.isEmpty()) {
            return StartExtractionResult.NoPartitionsSelected
        }

        // Step 2: Run preflight checks
        val preflightResult = preflightValidator.validate(
            selectedPartitions = selectedPartitions,
            outputDirectoryUri = request.outputDirectoryUri,
            isIncremental = request.isIncremental,
        )

        when (preflightResult) {
            is PreflightResult.Ready -> { /* continue */ }
            is PreflightResult.InsufficientStorage -> {
                return StartExtractionResult.PreflightFailed(preflightResult)
            }
            is PreflightResult.ActiveJobExists -> {
                return StartExtractionResult.PreflightFailed(preflightResult)
            }
            is PreflightResult.OutputDirectoryInaccessible -> {
                return StartExtractionResult.PreflightFailed(preflightResult)
            }
            is PreflightResult.SystemError -> {
                return StartExtractionResult.PreflightFailed(preflightResult)
            }
            is PreflightResult.IncrementalPrerequisitesUnmet -> {
                return StartExtractionResult.PreflightFailed(preflightResult)
            }
        }

        // Step 3: Create job entity
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val selectedIds = Json.encodeToString<List<String>>(request.selectedPartitionIds)

        val jobEntity = JobEntity(
            id = jobId,
            packageId = request.packageId,
            status = JobStatus.QUEUED.name,
            supportTierAtCreation = request.supportTier,
            selectedPartitionIds = selectedIds,
            outputDirectoryUri = request.outputDirectoryUri,
            createdAt = now,
            totalPartitions = selectedPartitions.size,
        )

        val createResult = jobRepository.createJob(jobEntity)
        if (createResult is DataResult.Error) {
            return StartExtractionResult.PreflightFailed(
                PreflightResult.ActiveJobExists,
            )
        }

        // Step 4: Create initial JobPhaseEntity records for each partition
        selectedPartitions.forEach { partition ->
            val phaseId = UUID.randomUUID().toString()
            val phase = JobPhaseEntity(
                id = phaseId,
                jobId = jobId,
                phase = JobPhaseType.EXTRACT.name,
                status = PhaseStatus.PENDING.name,
                partitionId = partition.id,
            )
            jobPhaseDao.insert(phase)
        }

        // Step 5: Enqueue WorkManager work
        val workData = Data.Builder()
            .putString(ExtractionWorker.KEY_JOB_ID, jobId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(workData)
            .addTag("extraction_$jobId")
            .build()

        // Store the WorkManager request UUID on the job for cancellation
        val jobWithWorkId = jobEntity.copy(
            workManagerRequestId = workRequest.id.toString(),
        )
        jobRepository.createJob(jobWithWorkId) // REPLACE upserts

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXTRACTION_WORK_NAME,
            ExistingWorkPolicy.KEEP, // Concurrent guard — only one runs
            workRequest,
        )

        return StartExtractionResult.Started(
            jobId = jobId,
            batteryWarning = (preflightResult as? PreflightResult.Ready)?.batteryWarning,
        )
    }
}

/**
 * Sealed result from extraction start attempt.
 */
sealed class StartExtractionResult {

    /** Extraction job created and enqueued. */
    data class Started(
        val jobId: String,
        val batteryWarning: String? = null,
    ) : StartExtractionResult()

    /** No partitions were selected. */
    data object NoPartitionsSelected : StartExtractionResult()

    /** Preflight validation failed. */
    data class PreflightFailed(
        val result: PreflightResult,
    ) : StartExtractionResult()
}
