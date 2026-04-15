package dev.forgeotalab.ui.screens.result

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.data.entity.ArtifactEntity
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.domain.ReExtractPartitionUseCase
import dev.forgeotalab.domain.ReExtractResult
import dev.forgeotalab.domain.ShareArtifactUseCase
import dev.forgeotalab.domain.ShareResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the extraction results screen.
 *
 * Responsibilities:
 * - Reconstruct result state from Room DB (survives process death — FR-9)
 * - Map artifacts and job status to sealed UI state
 * - Expose share and re-extract actions
 * - Enforce PRD Rule #3: no success display while any artifact is PENDING
 *
 * State management: StateFlow<ResultUiState> sealed class. No LiveData.
 *
 * Process death recovery: This ViewModel loads all state from Room via
 * jobId. There is zero in-memory-only state — if the app is killed and
 * restarted, navigating back to this screen with the same jobId
 * reconstructs the full result.
 *
 * Accessibility: All status text is semantic and complete for TalkBack.
 * Summary descriptions follow patterns like "5 of 7 partitions verified,
 * 2 failed verification" — not just counts.
 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val artifactRepository: ArtifactRepository,
    private val shareArtifactUseCase: ShareArtifactUseCase,
    private val reExtractPartitionUseCase: ReExtractPartitionUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    /** One-shot events for navigation and share intents. */
    private val _events = MutableSharedFlow<ResultEvent>()
    val events = _events.asSharedFlow()

    private var currentJobId: String? = null

    /**
     * Initialize or re-initialize with a job ID.
     *
     * WHY separate from constructor: Compose Navigation provides the jobId
     * via route arguments, and LaunchedEffect calls this. Using
     * SavedStateHandle directly in the constructor would also work, but
     * explicit initialization is clearer and easier to test.
     */
    fun initialize(jobId: String) {
        if (jobId == currentJobId) return
        currentJobId = jobId
        observeJobResult(jobId)
    }

    // =========================================================================
    // State observation
    // =========================================================================

    /**
     * Observe job state and artifact list from Room, combining into UI state.
     *
     * WHY combine two flows: The job status and artifacts can update
     * independently (e.g., job status changes to COMPLETED while artifacts
     * are still being written). Combining ensures the UI always reflects
     * the latest consistent state.
     */
    private fun observeJobResult(jobId: String) {
        viewModelScope.launch {
            combine(
                jobRepository.observeJobWithDetails(jobId).filterNotNull(),
                artifactRepository.observeArtifactsByJobSorted(jobId),
            ) { jobWithDetails, artifacts ->
                buildUiState(jobWithDetails.job, artifacts)
            }
                .catch { e ->
                    _uiState.value = ResultUiState.Error(
                        message = "Unable to load results: ${e.message}",
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun buildUiState(
        job: JobEntity,
        artifacts: List<ArtifactEntity>,
    ): ResultUiState {
        val verified = artifacts.count {
            it.verificationStatus == VerificationStatus.VERIFIED.name
        }
        val unverifiable = artifacts.count {
            it.verificationStatus == VerificationStatus.UNVERIFIABLE.name
        }
        val mismatched = artifacts.count {
            it.verificationStatus == VerificationStatus.MISMATCH.name
        }
        val pending = artifacts.count {
            it.verificationStatus == VerificationStatus.PENDING.name
        }
        val failed = job.failedPartitions

        // PRD Rule #3: no success display while any artifact is still PENDING.
        // If verification is still running, show a "verifying" state.
        if (pending > 0 && job.status == JobStatus.RUNNING.name) {
            return ResultUiState.Verifying(
                message = "Verifying extracted partitions… ($pending remaining)",
            )
        }

        val elapsed = (job.completedAt ?: System.currentTimeMillis()) - job.createdAt

        // Build summary text — the key sentence users read first
        val summaryText = buildSummaryText(
            verified = verified,
            unverifiable = unverifiable,
            mismatched = mismatched,
            failed = failed,
            total = job.totalPartitions,
            jobStatus = job.status,
        )

        val artifactItems = artifacts.map { artifact ->
            ResultArtifactItem(
                id = artifact.id,
                partitionName = artifact.partitionName,
                sizeFormatted = formatFileSize(artifact.sizeBytes),
                sizeBytes = artifact.sizeBytes,
                sha256 = artifact.sha256 ?: "",
                expectedHash = artifact.expectedHash ?: "",
                derivationType = artifact.derivationType,
                derivationLabel = formatDerivationType(artifact.derivationType),
                verificationStatus = artifact.verificationStatus,
                verificationLabel = formatVerificationLabel(
                    artifact.verificationStatus,
                    artifact.sha256,
                    artifact.expectedHash,
                ),
                outputUri = artifact.outputUri,
                sourcePackageDisplayName = artifact.sourcePackageDisplayName,
                warnings = parseWarnings(artifact.warnings),
                canShare = artifact.verificationStatus != VerificationStatus.PENDING.name,
                canReExtract = artifact.verificationStatus == VerificationStatus.MISMATCH.name,
                canBrowse = artifact.verificationStatus == VerificationStatus.VERIFIED.name,
            )
        }

        val data = ResultScreenData(
            jobId = job.id,
            jobStatus = job.status,
            summaryText = summaryText,
            verifiedCount = verified,
            unverifiableCount = unverifiable,
            mismatchCount = mismatched,
            failedCount = failed,
            totalPartitions = job.totalPartitions,
            durationFormatted = formatDuration(elapsed),
            artifacts = artifactItems,
        )

        return ResultUiState.Loaded(data = data)
    }

    // =========================================================================
    // Summary text builder
    // =========================================================================

    private fun buildSummaryText(
        verified: Int,
        unverifiable: Int,
        mismatched: Int,
        failed: Int,
        total: Int,
        jobStatus: String,
    ): String {
        val successCount = verified + unverifiable

        return when (jobStatus) {
            JobStatus.COMPLETED.name -> {
                if (verified == total) {
                    "$total of $total partitions verified"
                } else {
                    "$verified of $total verified" +
                        if (unverifiable > 0) ", $unverifiable unverifiable" else ""
                }
            }
            JobStatus.PARTIAL_SUCCESS.name -> {
                val problems = mutableListOf<String>()
                if (mismatched > 0) problems.add("$mismatched failed verification")
                if (failed > 0) problems.add("$failed failed extraction")
                "$successCount of $total — ${problems.joinToString(", ")}"
            }
            JobStatus.FAILED.name -> {
                "All $total partitions failed"
            }
            JobStatus.CANCELED.name -> {
                if (successCount > 0) {
                    "Canceled — $successCount verified partitions preserved"
                } else {
                    "Extraction canceled"
                }
            }
            else -> "$successCount of $total partitions complete"
        }
    }

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Share an extracted artifact via ACTION_SEND.
     * PRD FR-7: "Share intent includes checksum in the share text."
     */
    fun shareArtifact(artifactId: String) {
        viewModelScope.launch {
            when (val result = shareArtifactUseCase.buildShareIntent(artifactId)) {
                is ShareResult.Ready -> {
                    _events.emit(ResultEvent.LaunchShareIntent(result.intent))
                }
                is ShareResult.Error -> {
                    _events.emit(ResultEvent.ShowSnackbar(result.message))
                }
            }
        }
    }

    /**
     * Re-extract a single partition that failed verification.
     * PRD FR-7: "Offer single-partition re-extract for hash mismatches."
     */
    fun reExtractPartition(artifactId: String) {
        viewModelScope.launch {
            when (val result = reExtractPartitionUseCase.execute(artifactId)) {
                is ReExtractResult.Enqueued -> {
                    _events.emit(
                        ResultEvent.ShowSnackbar(
                            "Re-extracting ${result.partitionName}…",
                        ),
                    )
                }
                is ReExtractResult.Error -> {
                    _events.emit(ResultEvent.ShowSnackbar(result.message))
                }
            }
        }
    }

    // =========================================================================
    // Formatting
    // =========================================================================

    private fun formatVerificationLabel(
        status: String,
        actualHash: String?,
        expectedHash: String?,
    ): String = when (status) {
        VerificationStatus.VERIFIED.name -> "SHA-256 verified ✓"
        VerificationStatus.MISMATCH.name -> {
            val expected = expectedHash?.take(16) ?: "unknown"
            val actual = actualHash?.take(16) ?: "unknown"
            "Hash mismatch ⚠ expected: $expected…, actual: $actual…"
        }
        VerificationStatus.UNVERIFIABLE.name -> "— No target hash available"
        else -> "Verification pending…"
    }

    private fun formatDerivationType(type: String): String = when (type) {
        "DIRECT" -> "Direct"
        "RECONSTRUCTED" -> "Reconstructed"
        "PARTIAL" -> "Partial"
        "RAW_UNVERIFIED" -> "Raw (unverified)"
        else -> type
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1024} KB"
        bytes < 1_073_741_824 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    }

    private fun parseWarnings(warningsJson: String?): List<String> {
        if (warningsJson.isNullOrBlank()) return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString(warningsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// =============================================================================
// Sealed UI State
// =============================================================================

/**
 * Sealed UI state for the result screen.
 *
 * PRD Rule #3: No success display while artifacts are still PENDING.
 * This is enforced by the [Verifying] state.
 */
sealed class ResultUiState {
    /** Reconstructing state from Room after process death or initial load. */
    data object Loading : ResultUiState()

    /** Verification still in progress for some artifacts. */
    data class Verifying(val message: String) : ResultUiState()

    /** Full result data available. */
    data class Loaded(val data: ResultScreenData) : ResultUiState()

    /** Room read failure or data inconsistency. */
    data class Error(val message: String) : ResultUiState()
}

// =============================================================================
// Events — one-shot side effects
// =============================================================================

sealed class ResultEvent {
    data class LaunchShareIntent(val intent: Intent) : ResultEvent()
    data class ShowSnackbar(val message: String) : ResultEvent()
}

// =============================================================================
// Display data models
// =============================================================================

@Immutable
data class ResultScreenData(
    val jobId: String,
    val jobStatus: String,
    val summaryText: String,
    val verifiedCount: Int,
    val unverifiableCount: Int,
    val mismatchCount: Int,
    val failedCount: Int,
    val totalPartitions: Int,
    val durationFormatted: String,
    val artifacts: List<ResultArtifactItem>,
)

@Immutable
data class ResultArtifactItem(
    val id: String,
    val partitionName: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val sha256: String,
    val expectedHash: String,
    val derivationType: String,
    val derivationLabel: String,
    val verificationStatus: String,
    val verificationLabel: String,
    val outputUri: String,
    val sourcePackageDisplayName: String?,
    val warnings: List<String>,
    val canShare: Boolean,
    val canReExtract: Boolean,
    val canBrowse: Boolean,
)
