package dev.forgeotalab.ui.screens.extraction

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.ExtractionRequest
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.data.entity.ArtifactEntity
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobPhaseEntity
import dev.forgeotalab.data.entity.PartitionEntity
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.domain.CancelExtractionUseCase
import dev.forgeotalab.domain.PreflightResult
import dev.forgeotalab.domain.StartExtractionResult
import dev.forgeotalab.domain.StartExtractionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the extraction screen.
 *
 * Responsibilities:
 * - Accept SAF output directory URI from the screen
 * - Run preflight validation and start extraction via use cases
 * - Observe job progress from Room and map to sealed UI state
 * - Handle cancellation requests
 * - Calculate elapsed time and ETA
 *
 * State management: StateFlow<ExtractionUiState> sealed class. No LiveData.
 *
 * Accessibility: All status text from this ViewModel is semantic and complete
 * for TalkBack announcement. Progress descriptions include the percentage,
 * partition name, and count ("Extracting system, 3 of 7 partitions, 42%").
 */
@HiltViewModel
class ExtractionViewModel @Inject constructor(
    private val startExtractionUseCase: StartExtractionUseCase,
    private val cancelExtractionUseCase: CancelExtractionUseCase,
    private val jobRepository: JobRepository,
    private val packageRepository: PackageRepository,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExtractionUiState>(
        ExtractionUiState.Loading("Preparing extraction…"),
    )
    val uiState: StateFlow<ExtractionUiState> = _uiState.asStateFlow()

    /**
     * One-shot navigation event: emitted when extraction reaches a terminal
     * state to trigger navigation to the dedicated ResultScreen.
     *
     * WHY SharedFlow: Navigation must happen exactly once. Using StateFlow
     * would re-trigger navigation on configuration change. SharedFlow with
     * replay=0 ensures fire-and-forget semantics.
     */
    private val _navigateToResult = MutableSharedFlow<String>()
    val navigateToResult = _navigateToResult.asSharedFlow()

    private var currentJobId: String? = null
    private var extractionStartTime: Long = 0L

    /**
     * Initialize the extraction screen with a package.
     *
     * This is called from the Composable with the navigation-provided packageId.
     * It loads the package data and waits for the user to select an output
     * directory before starting extraction.
     */
    fun initialize(packageId: String) {
        viewModelScope.launch {
            _uiState.value = ExtractionUiState.WaitingForDirectory(packageId = packageId)
        }
    }

    /**
     * Start extraction after the user has selected an output directory.
     *
     * @param packageId UUID of the package to extract.
     * @param selectedPartitionIds List of partition UUIDs selected for extraction.
     * @param outputDirectoryUri SAF URI of the user-selected output directory.
     * @param supportTier Support tier snapshot at extraction time.
     * @param isIncremental Whether this is an incremental package.
     */
    fun startExtraction(
        packageId: String,
        selectedPartitionIds: List<String>,
        outputDirectoryUri: String,
        supportTier: String,
        isIncremental: Boolean,
    ) {
        viewModelScope.launch {
            _uiState.value = ExtractionUiState.Loading("Running preflight checks…")

            val request = ExtractionRequest(
                packageId = packageId,
                selectedPartitionIds = selectedPartitionIds,
                outputDirectoryUri = outputDirectoryUri,
                supportTier = supportTier,
                isIncremental = isIncremental,
            )

            when (val result = startExtractionUseCase.execute(request)) {
                is StartExtractionResult.Started -> {
                    currentJobId = result.jobId
                    extractionStartTime = System.currentTimeMillis()
                    observeJobProgress(result.jobId)

                    // Show battery warning if applicable
                    if (result.batteryWarning != null) {
                        _uiState.value = ExtractionUiState.Running(
                            data = ExtractionProgressData(
                                jobId = result.jobId,
                                currentPartition = "Starting…",
                                completedPartitions = 0,
                                totalPartitions = selectedPartitionIds.size,
                                overallPercent = 0,
                                elapsedTime = "0:00",
                                estimatedTimeRemaining = null,
                                partitionStatuses = emptyList(),
                                batteryWarning = result.batteryWarning,
                            ),
                        )
                    }
                }

                is StartExtractionResult.NoPartitionsSelected -> {
                    _uiState.value = ExtractionUiState.PreflightFailed(
                        message = "No partitions selected for extraction.",
                        actionLabel = "Go back and select partitions",
                    )
                }

                is StartExtractionResult.PreflightFailed -> {
                    val (message, actionLabel) = mapPreflightFailure(result.result)
                    _uiState.value = ExtractionUiState.PreflightFailed(
                        message = message,
                        actionLabel = actionLabel,
                    )
                }
            }
        }
    }

    /**
     * Cancel the currently running extraction.
     *
     * PRD: "Cancellation preserves verified outputs."
     */
    fun cancelExtraction() {
        val jobId = currentJobId ?: return
        viewModelScope.launch {
            cancelExtractionUseCase.execute(jobId)
            // State will update via the observeJobProgress flow
        }
    }

    // =========================================================================
    // Job observation
    // =========================================================================

    /**
     * Observe job progress via Room Flow and map to UI state.
     *
     * WHY observeJobWithDetails: Single query returns job + phases + artifacts
     * without N+1. Room emits updates whenever any entity in the query changes.
     */
    private fun observeJobProgress(jobId: String) {
        viewModelScope.launch {
            jobRepository.observeJobWithDetails(jobId)
                .filterNotNull()
                .catch { e ->
                    _uiState.value = ExtractionUiState.Error(
                        message = "Lost connection to extraction job: ${e.message}",
                    )
                }
                .collect { jobWithDetails ->
                    val job = jobWithDetails.job
                    val phases = jobWithDetails.phases
                    val artifacts = jobWithDetails.artifacts

                    when (job.status) {
                        JobStatus.QUEUED.name -> {
                            _uiState.value = ExtractionUiState.Loading("Waiting to start…")
                        }

                        JobStatus.RUNNING.name -> {
                            _uiState.value = ExtractionUiState.Running(
                                data = buildProgressData(job, phases, artifacts),
                            )
                        }

                        JobStatus.COMPLETED.name -> {
                            _uiState.value = ExtractionUiState.Completed(
                                data = buildResultData(job, artifacts),
                            )
                            _navigateToResult.emit(job.id)
                        }

                        JobStatus.PARTIAL_SUCCESS.name -> {
                            _uiState.value = ExtractionUiState.PartialSuccess(
                                data = buildResultData(job, artifacts),
                            )
                            _navigateToResult.emit(job.id)
                        }

                        JobStatus.FAILED.name -> {
                            _uiState.value = ExtractionUiState.Failed(
                                data = buildResultData(job, artifacts),
                            )
                            _navigateToResult.emit(job.id)
                        }

                        JobStatus.CANCELED.name -> {
                            _uiState.value = ExtractionUiState.Canceled(
                                data = buildResultData(job, artifacts),
                            )
                            _navigateToResult.emit(job.id)
                        }
                    }
                }
        }
    }

    // =========================================================================
    // State mapping
    // =========================================================================

    private fun buildProgressData(
        job: JobEntity,
        phases: List<JobPhaseEntity>,
        artifacts: List<ArtifactEntity>,
    ): ExtractionProgressData {
        val completed = job.completedPartitions
        val total = job.totalPartitions
        val overallPercent = if (total > 0) (completed * 100) / total else 0
        val elapsed = System.currentTimeMillis() - extractionStartTime

        // Find currently running phase
        val currentPhase = phases.lastOrNull { it.status == "RUNNING" }
        val currentPartition = currentPhase?.partitionId ?: "Processing…"

        // Estimate remaining time
        val estimatedRemaining = if (completed > 0 && total > completed) {
            val msPerPartition = elapsed / completed
            val remaining = (total - completed) * msPerPartition
            formatDuration(remaining)
        } else null

        // Build partition status list
        val partitionStatuses = phases.map { phase ->
            val artifact = artifacts.find { it.partitionId == phase.partitionId }
            PartitionProgressItem(
                partitionId = phase.partitionId ?: "",
                partitionName = artifact?.partitionName ?: phase.partitionId ?: "Unknown",
                status = phase.status,
                percent = phase.progressPercent,
                verificationStatus = artifact?.verificationStatus,
            )
        }

        return ExtractionProgressData(
            jobId = job.id,
            currentPartition = currentPartition,
            completedPartitions = completed,
            totalPartitions = total,
            overallPercent = overallPercent,
            elapsedTime = formatDuration(elapsed),
            estimatedTimeRemaining = estimatedRemaining,
            partitionStatuses = partitionStatuses,
        )
    }

    private fun buildResultData(
        job: JobEntity,
        artifacts: List<ArtifactEntity>,
    ): ExtractionResultData {
        val verified = artifacts.count {
            it.verificationStatus == VerificationStatus.VERIFIED.name
        }
        val unverifiable = artifacts.count {
            it.verificationStatus == VerificationStatus.UNVERIFIABLE.name
        }
        val mismatched = artifacts.count {
            it.verificationStatus == VerificationStatus.MISMATCH.name
        }
        val failed = job.failedPartitions

        val elapsed = (job.completedAt ?: System.currentTimeMillis()) -
            job.createdAt

        return ExtractionResultData(
            jobId = job.id,
            status = job.status,
            verifiedCount = verified,
            unverifiableCount = unverifiable,
            mismatchCount = mismatched,
            failedCount = failed,
            totalPartitions = job.totalPartitions,
            durationFormatted = formatDuration(elapsed),
            artifacts = artifacts.map { artifact ->
                ArtifactDisplayItem(
                    id = artifact.id,
                    partitionName = artifact.partitionName,
                    sizeFormatted = formatFileSize(artifact.sizeBytes),
                    sizeBytes = artifact.sizeBytes,
                    sha256 = artifact.sha256 ?: "",
                    derivationType = artifact.derivationType,
                    verificationStatus = artifact.verificationStatus,
                    outputUri = artifact.outputUri,
                )
            },
        )
    }

    private fun mapPreflightFailure(result: PreflightResult): Pair<String, String> {
        return when (result) {
            is PreflightResult.InsufficientStorage -> {
                val needed = formatFileSize(result.estimate.requiredBytes)
                val available = formatFileSize(result.estimate.availableBytes)
                val deficit = formatFileSize(result.estimate.deficitBytes)
                "Insufficient storage: need $needed, only $available available. " +
                    "Free $deficit and try again." to "Free storage"
            }
            is PreflightResult.ActiveJobExists -> {
                "Another extraction is already running. " +
                    "Only one extraction job can be active at a time." to "View active job"
            }
            is PreflightResult.OutputDirectoryInaccessible -> {
                "Cannot access the output directory. " +
                    "Permission may have been revoked." to "Select a new directory"
            }
            is PreflightResult.IncrementalPrerequisitesUnmet -> {
                val count = result.unmatchedPartitions.size
                val names = result.unmatchedPartitions.joinToString(", ")
                "$count partition(s) still require validated base images: $names. " +
                    "Complete the prerequisite wizard first." to "Open wizard"
            }
            is PreflightResult.SystemError -> {
                result.message to "Retry"
            }
            is PreflightResult.Ready -> {
                "Ready" to "Start" // Should not reach here
            }
        }
    }

    // =========================================================================
    // Formatting
    // =========================================================================

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1_048_576 -> "${bytes / 1024} KB"
            bytes < 1_073_741_824 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            else -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
        }
    }
}

// =============================================================================
// Sealed UI State
// =============================================================================

/**
 * Sealed UI state for the extraction screen.
 *
 * Each variant maps to a specific visual treatment per PRD State Matrix.
 */
sealed class ExtractionUiState {
    /** Loading or transitional state. */
    data class Loading(val message: String) : ExtractionUiState()

    /** Waiting for user to select SAF output directory. */
    data class WaitingForDirectory(val packageId: String) : ExtractionUiState()

    /** Preflight validation failed — show specific, actionable error. */
    data class PreflightFailed(
        val message: String,
        val actionLabel: String,
    ) : ExtractionUiState()

    /** Extraction actively running — show progress. */
    data class Running(val data: ExtractionProgressData) : ExtractionUiState()

    /** All partitions extracted and verified. */
    data class Completed(val data: ExtractionResultData) : ExtractionUiState()

    /** Some partitions verified, others failed — first-class outcome. */
    data class PartialSuccess(val data: ExtractionResultData) : ExtractionUiState()

    /** All partitions failed. */
    data class Failed(val data: ExtractionResultData) : ExtractionUiState()

    /** User-initiated cancellation — verified outputs preserved. */
    data class Canceled(val data: ExtractionResultData) : ExtractionUiState()

    /** System error. */
    data class Error(val message: String) : ExtractionUiState()
}

// =============================================================================
// Display data models
// =============================================================================

@Stable
data class ExtractionProgressData(
    val jobId: String,
    val currentPartition: String,
    val completedPartitions: Int,
    val totalPartitions: Int,
    val overallPercent: Int,
    val elapsedTime: String,
    val estimatedTimeRemaining: String?,
    val partitionStatuses: List<PartitionProgressItem>,
    val batteryWarning: String? = null,
)

@Immutable
data class PartitionProgressItem(
    val partitionId: String,
    val partitionName: String,
    val status: String,
    val percent: Int,
    val verificationStatus: String?,
)

@Immutable
data class ExtractionResultData(
    val jobId: String,
    val status: String,
    val verifiedCount: Int,
    val unverifiableCount: Int,
    val mismatchCount: Int,
    val failedCount: Int,
    val totalPartitions: Int,
    val durationFormatted: String,
    val artifacts: List<ArtifactDisplayItem>,
)

@Immutable
data class ArtifactDisplayItem(
    val id: String,
    val partitionName: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val sha256: String,
    val derivationType: String,
    val verificationStatus: String,
    val outputUri: String,
)
