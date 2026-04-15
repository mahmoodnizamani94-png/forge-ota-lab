package dev.forgeotalab.ui.screens.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.data.entity.HistoryItem
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.data.repository.getOrNull
import dev.forgeotalab.domain.CleanupInterruptedJobUseCase
import dev.forgeotalab.domain.CleanupResult
import dev.forgeotalab.domain.ImportPackageUseCase
import dev.forgeotalab.domain.ImportResult
import dev.forgeotalab.domain.ResumeExtractionUseCase
import dev.forgeotalab.domain.ResumeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Home / job history screen.
 *
 * State management: StateFlow<HomeUiState> with sealed class variants.
 * No LiveData per PRD stack constraints.
 *
 * History management responsibilities:
 * - Observe recent packages from Room (reactive)
 * - Observe interrupted/resumable jobs (FR-9)
 * - Check URI accessibility per history item (FR-11)
 * - Swipe-to-delete with undo
 * - Deep link navigation to analysis/result screens
 * - Resume and cleanup actions for interrupted jobs
 *
 * Accessibility: All display strings in PackageHistoryItem are pre-formatted
 * for TalkBack consumption with semantic content descriptions.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val packageRepository: PackageRepository,
    private val jobRepository: JobRepository,
    private val importPackageUseCase: ImportPackageUseCase,
    private val resumeExtractionUseCase: ResumeExtractionUseCase,
    private val cleanupInterruptedJobUseCase: CleanupInterruptedJobUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _resumeState = MutableStateFlow<ResumeUiState>(ResumeUiState.Idle)
    val resumeState: StateFlow<ResumeUiState> = _resumeState.asStateFlow()

    /**
     * Cached package entity for swipe-to-delete undo.
     *
     * WHY cache the full entity: After deletion, we need the complete record
     * to re-insert on undo. Querying Room after deletion returns nothing.
     * The cache is short-lived — cleared on the next deletion or undo.
     */
    private var lastDeletedPackage: PackageEntity? = null

    init {
        observeHistory()
        detectInterruptedJobs()
    }

    // =========================================================================
    // History observation
    // =========================================================================

    /**
     * Observe recent packages and resumable jobs from Room — reactive.
     *
     * WHY combine with resumableJobs: The home screen needs both the history
     * list and the interrupted jobs banner. Combining the flows ensures
     * both update atomically when either source changes.
     */
    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                packageRepository.observeRecentPackages(limit = 100),
                jobRepository.observeResumableJobs(),
            ) { packages, resumableJobs ->
                Pair(packages, resumableJobs)
            }
                .catch { e ->
                    _uiState.value = HomeUiState.Error(
                        message = "Unable to load history. Tap to retry.",
                    )
                }
                .collect { (packages, resumableJobs) ->
                    if (packages.isEmpty() && resumableJobs.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        val historyItems = packages.map { pkg ->
                            buildHistoryItem(pkg, resumableJobs)
                        }
                        val interruptedItems = resumableJobs
                            .filter { it.status == JobStatus.INTERRUPTED.name }
                            .map { buildResumableJobItem(it) }

                        _uiState.value = HomeUiState.Loaded(
                            packages = historyItems,
                            interruptedJobs = interruptedItems,
                        )
                    }
                }
        }
    }

    /**
     * On startup, detect stale RUNNING jobs and mark them as INTERRUPTED.
     *
     * WHY here and not in the worker: The worker runs only when scheduled.
     * If the app is opened without triggering a new extraction, stale
     * RUNNING jobs would never be detected. The ViewModel catches them
     * on every app launch.
     */
    private fun detectInterruptedJobs() {
        viewModelScope.launch {
            jobRepository.markAllRunningAsInterrupted()
        }
    }

    // =========================================================================
    // History item builders
    // =========================================================================

    /**
     * Build a display-ready history item from a package entity.
     *
     * WHY async URI check: Probing SAF URIs via ContentResolver is blocking I/O.
     * We check on a background thread and update the item's accessibility state.
     * For the initial render, we assume accessible — the check runs concurrently.
     */
    private suspend fun buildHistoryItem(
        pkg: PackageEntity,
        resumableJobs: List<JobEntity>,
    ): PackageHistoryItem {
        val latestJob = jobRepository.getLatestJobForPackage(pkg.id).getOrNull()
        val isAccessible = packageRepository.checkUriAccessible(pkg.sourceUri)

        val tier = try {
            SupportTier.valueOf(pkg.supportTier)
        } catch (_: Exception) {
            SupportTier.FORENSIC
        }

        return PackageHistoryItem(
            id = pkg.id,
            displayName = pkg.displayName,
            supportTier = tier,
            classification = pkg.classification,
            partitionCount = 0, // Partition count loaded separately if needed
            lastOpenedFormatted = formatTimestamp(pkg.lastOpenedAt),
            analysisComplete = pkg.analysisComplete,
            fileSizeFormatted = formatFileSize(pkg.fileSizeBytes),
            securityPatchLevel = pkg.securityPatchLevel,
            isIncremental = pkg.isIncremental,
            latestJobId = latestJob?.id,
            latestJobStatus = latestJob?.status?.let { statusName ->
                try { JobStatus.valueOf(statusName) } catch (_: Exception) { null }
            },
            completedPartitions = latestJob?.completedPartitions ?: 0,
            totalPartitions = latestJob?.totalPartitions ?: 0,
            isUriAccessible = isAccessible,
        )
    }

    /**
     * Build a resumable job item for the interrupted jobs banner.
     */
    private suspend fun buildResumableJobItem(job: JobEntity): ResumableJobItem {
        val pkg = packageRepository.getPackageById(job.packageId).getOrNull()
        return ResumableJobItem(
            jobId = job.id,
            packageId = job.packageId,
            packageDisplayName = pkg?.displayName ?: "Unknown package",
            completedPartitions = job.completedPartitions,
            totalPartitions = job.totalPartitions,
        )
    }

    // =========================================================================
    // Import
    // =========================================================================

    /**
     * Import a file from SAF picker, share intent, or open-with handler.
     *
     * The import flow runs in the background. On success, returns the
     * package ID for navigation to the analysis screen.
     */
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Analyzing

            when (val result = importPackageUseCase.execute(uri)) {
                is ImportResult.Success -> {
                    _importState.value = ImportState.Success(
                        packageId = result.packageId,
                    )
                }
                is ImportResult.UriInaccessible -> {
                    _importState.value = ImportState.Error(
                        message = result.message,
                    )
                }
                is ImportResult.AnalysisFailed -> {
                    _importState.value = ImportState.Error(
                        message = result.message,
                    )
                }
            }
        }
    }

    /** Reset import state after navigation or error dismissal. */
    fun consumeImportResult() {
        _importState.value = ImportState.Idle
    }

    /** Retry loading history after a DB error. */
    fun retryLoadHistory() {
        _uiState.value = HomeUiState.Loading
        observeHistory()
    }

    // =========================================================================
    // Swipe-to-delete with undo (FR-11)
    // =========================================================================

    /**
     * Delete a history item (swipe-to-delete).
     *
     * WHY cache before delete: The Snackbar undo action needs the full
     * PackageEntity to re-insert. After deletion, the entity is gone from
     * Room. We cache it here and clear on the next deletion or undo.
     */
    fun deleteHistoryItem(packageId: String) {
        viewModelScope.launch {
            // Cache the entity for undo
            val pkg = packageRepository.getPackageById(packageId).getOrNull()
            lastDeletedPackage = pkg

            // Delete from Room — cascades to jobs, phases, artifacts
            packageRepository.deletePackage(packageId)
        }
    }

    /**
     * Undo a swipe-to-delete — re-inserts the cached entity.
     */
    fun undoDeleteHistoryItem() {
        val pkg = lastDeletedPackage ?: return
        lastDeletedPackage = null

        viewModelScope.launch {
            packageRepository.importPackage(pkg)
        }
    }

    /** Clear the undo cache (e.g., when Snackbar times out). */
    fun clearUndoState() {
        lastDeletedPackage = null
    }

    // =========================================================================
    // Resume and cleanup (FR-9)
    // =========================================================================

    /**
     * Resume an interrupted extraction job.
     */
    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            _resumeState.value = ResumeUiState.Resuming

            when (val result = resumeExtractionUseCase.execute(jobId)) {
                is ResumeResult.Resumed -> {
                    _resumeState.value = ResumeUiState.Resumed(
                        jobId = result.jobId,
                        skippedCount = result.skippedCount,
                        totalPartitions = result.totalPartitions,
                    )
                }
                is ResumeResult.SourceRevoked -> {
                    _resumeState.value = ResumeUiState.SourceRevoked(
                        jobId = result.jobId,
                        packageDisplayName = result.packageDisplayName,
                    )
                }
                is ResumeResult.CheckpointCorrupted -> {
                    _resumeState.value = ResumeUiState.CheckpointCorrupted(result.jobId)
                }
                is ResumeResult.JobNotFound,
                is ResumeResult.NotResumable,
                is ResumeResult.PackageNotFound -> {
                    _resumeState.value = ResumeUiState.Error("Job is no longer available.")
                }
                is ResumeResult.Error -> {
                    _resumeState.value = ResumeUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Clean up an interrupted job — preserves verified outputs, removes temp files.
     */
    fun cleanupJob(jobId: String) {
        viewModelScope.launch {
            when (val result = cleanupInterruptedJobUseCase.execute(jobId)) {
                is CleanupResult.Cleaned -> {
                    _resumeState.value = ResumeUiState.CleanedUp(
                        preservedCount = result.preservedArtifactCount,
                    )
                }
                is CleanupResult.JobNotFound -> {
                    _resumeState.value = ResumeUiState.Error("Job not found.")
                }
            }
        }
    }

    /** Reset resume state after consuming the result. */
    fun consumeResumeResult() {
        _resumeState.value = ResumeUiState.Idle
    }

    // =========================================================================
    // Navigation helpers
    // =========================================================================

    /**
     * Determine the correct navigation destination for a history item tap.
     *
     * PRD FR-11: "Tapping re-opens the result or analysis screen."
     */
    fun getNavigationTarget(item: PackageHistoryItem): NavigationTarget {
        // URI revoked — prompt re-import
        if (!item.isUriAccessible) {
            return NavigationTarget.UriRevoked(item.id)
        }

        // Has a completed/partial job — navigate to result
        val jobStatus = item.latestJobStatus
        if (jobStatus != null && item.latestJobId != null) {
            return when (jobStatus) {
                JobStatus.COMPLETED,
                JobStatus.PARTIAL_SUCCESS,
                JobStatus.FAILED -> NavigationTarget.Result(item.latestJobId)

                JobStatus.INTERRUPTED,
                JobStatus.PAUSED -> NavigationTarget.Interrupted(item.latestJobId)

                JobStatus.RUNNING,
                JobStatus.QUEUED -> NavigationTarget.ActiveJob(item.latestJobId)

                JobStatus.CANCELED -> NavigationTarget.Analysis(item.id)
            }
        }

        // Default — analysis screen
        return NavigationTarget.Analysis(item.id)
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    private fun formatTimestamp(epochMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMs
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
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
// UI State classes — sealed for exhaustive when handling
// =============================================================================

/**
 * Sealed UI state for the home screen.
 * New variants cause compile errors at every `when` site.
 */
sealed class HomeUiState {
    /** Initial loading — checking Room for existing packages. */
    data object Loading : HomeUiState()

    /** No packages imported yet — show branded empty state. */
    data object Empty : HomeUiState()

    /** History loaded with packages and optional interrupted jobs banner. */
    data class Loaded(
        val packages: List<PackageHistoryItem>,
        val interruptedJobs: List<ResumableJobItem> = emptyList(),
    ) : HomeUiState()

    /** DB read failure — show retry action. */
    data class Error(val message: String) : HomeUiState()
}

/**
 * Import operation state — separate from HomeUiState because import
 * overlays the current screen state.
 */
sealed class ImportState {
    /** No import in progress. */
    data object Idle : ImportState()

    /** Analysis running — "Analyzing file…" overlay. */
    data object Analyzing : ImportState()

    /** Import succeeded — ready to navigate to analysis. */
    data class Success(val packageId: String) : ImportState()

    /** Import failed — show error snackbar. */
    data class Error(val message: String) : ImportState()
}

/**
 * Resume operation state — separate from HomeUiState because resume
 * results overlay as snackbars or dialogs.
 */
sealed class ResumeUiState {
    /** No resume in progress. */
    data object Idle : ResumeUiState()

    /** Resume is being processed. */
    data object Resuming : ResumeUiState()

    /** Resume succeeded — navigate to extraction monitor. */
    data class Resumed(
        val jobId: String,
        val skippedCount: Int,
        val totalPartitions: Int,
    ) : ResumeUiState()

    /** Source URI revoked — need re-import. */
    data class SourceRevoked(
        val jobId: String,
        val packageDisplayName: String,
    ) : ResumeUiState()

    /** Checkpoint corrupted — offer cleanup. */
    data class CheckpointCorrupted(val jobId: String) : ResumeUiState()

    /** Cleanup completed — show preserved artifact count. */
    data class CleanedUp(val preservedCount: Int) : ResumeUiState()

    /** Resume failed with error. */
    data class Error(val message: String) : ResumeUiState()
}

/**
 * Display model for a package in the history list.
 *
 * WHY a separate display model: Decouples Room entity schema from UI
 * rendering. The ViewModel pre-formats timestamps, file sizes, and badge
 * content so the Composable only renders — no business logic in the UI layer.
 */
@Immutable
data class PackageHistoryItem(
    val id: String,
    val displayName: String,
    val supportTier: SupportTier,
    val classification: String,
    val partitionCount: Int,
    val lastOpenedFormatted: String,
    val analysisComplete: Boolean,
    val fileSizeFormatted: String,
    val securityPatchLevel: String?,
    val isIncremental: Boolean,
    /** Latest job ID — for deep link navigation. */
    val latestJobId: String? = null,
    /** Latest job status — for badge display and navigation routing. */
    val latestJobStatus: JobStatus? = null,
    /** Completed partitions from latest job — for progress display. */
    val completedPartitions: Int = 0,
    /** Total partitions from latest job. */
    val totalPartitions: Int = 0,
    /** Whether the source URI is still accessible via SAF. */
    val isUriAccessible: Boolean = true,
)

/**
 * Display model for an interrupted job in the resume banner.
 */
@Immutable
data class ResumableJobItem(
    val jobId: String,
    val packageId: String,
    val packageDisplayName: String,
    val completedPartitions: Int,
    val totalPartitions: Int,
)

/**
 * Navigation target resolved from a history item tap.
 */
sealed class NavigationTarget {
    /** Navigate to analysis screen for a package. */
    data class Analysis(val packageId: String) : NavigationTarget()

    /** Navigate to result screen for a completed job. */
    data class Result(val jobId: String) : NavigationTarget()

    /** Show resume dialog for an interrupted job. */
    data class Interrupted(val jobId: String) : NavigationTarget()

    /** Navigate to live job monitor for an active job. */
    data class ActiveJob(val jobId: String) : NavigationTarget()

    /** Show re-import prompt for a URI-revoked package. */
    data class UriRevoked(val packageId: String) : NavigationTarget()
}
