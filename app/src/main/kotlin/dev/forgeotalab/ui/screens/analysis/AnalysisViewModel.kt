package dev.forgeotalab.ui.screens.analysis

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.PartitionCategory
import dev.forgeotalab.contracts.model.StorageEstimate
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PartitionEntity
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.domain.StorageEstimateCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the analysis screen.
 *
 * Responsibilities:
 * - Load package + partitions from Room (N+1-free via PackageWithPartitions)
 * - Group partitions by category (FR-6)
 * - Manage partition selection state with instant storage recalculation
 * - Apply presets (Boot set, System analysis, Everything extractable)
 * - Calculate storage estimates per PRD NFR-6
 *
 * State management: StateFlow<AnalysisUiState> sealed class. No LiveData.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val packageRepository: PackageRepository,
    private val storageEstimateCalculator: StorageEstimateCalculator,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading("Analyzing file…"))
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _selectedPartitions = MutableStateFlow<Set<String>>(emptySet())
    val selectedPartitions: StateFlow<Set<String>> = _selectedPartitions.asStateFlow()

    private val _storageEstimate = MutableStateFlow<StorageEstimate?>(null)
    val storageEstimate: StateFlow<StorageEstimate?> = _storageEstimate.asStateFlow()

    private var currentPartitions: List<PartitionEntity> = emptyList()
    private var isIncremental: Boolean = false

    /**
     * Load analysis data for a package.
     *
     * WHY not init: The packageId comes from navigation arguments,
     * which may not be available at ViewModel creation time for all
     * navigation patterns.
     */
    fun loadAnalysis(packageId: String) {
        viewModelScope.launch {
            packageRepository.observePackageWithPartitions(packageId)
                .filterNotNull()
                .catch { e ->
                    _uiState.value = AnalysisUiState.Error(
                        message = "Unable to load analysis data. Tap to retry.",
                    )
                }
                .collect { pkgWithPartitions ->
                    val pkg = pkgWithPartitions.pkg
                    val partitions = pkgWithPartitions.partitions

                    currentPartitions = partitions
                    isIncremental = pkg.isIncremental

                    val tier = try {
                        SupportTier.valueOf(pkg.supportTier)
                    } catch (_: Exception) {
                        SupportTier.FORENSIC
                    }

                    val screenData = buildScreenData(pkg, partitions, tier)

                    _uiState.value = when (tier) {
                        SupportTier.SUPPORTED -> AnalysisUiState.Supported(data = screenData)
                        SupportTier.EXPERIMENTAL -> AnalysisUiState.Experimental(data = screenData)
                        SupportTier.FORENSIC -> AnalysisUiState.Forensic(data = screenData)
                    }

                    // Auto-select extractable partitions
                    if (_selectedPartitions.value.isEmpty()) {
                        val extractable = partitions
                            .filter { it.isExtractable }
                            .map { it.id }
                            .toSet()
                        _selectedPartitions.value = extractable
                        recalculateStorage()
                    }
                }
        }
    }

    // =========================================================================
    // Selection management
    // =========================================================================

    /** Toggle a single partition selection. */
    fun togglePartition(partitionId: String) {
        val current = _selectedPartitions.value.toMutableSet()
        if (current.contains(partitionId)) {
            current.remove(partitionId)
        } else {
            current.add(partitionId)
        }
        _selectedPartitions.value = current
        recalculateStorage()
    }

    /** Apply the "Boot set" preset: boot + init_boot + vendor_boot + vbmeta + dtbo. */
    fun applyBootPreset() {
        val bootPartitions = currentPartitions
            .filter { it.category == PartitionCategory.BOOT_CRITICAL.name && it.isExtractable }
            .map { it.id }
            .toSet()
        _selectedPartitions.value = bootPartitions
        recalculateStorage()
    }

    /** Apply the "System analysis set" preset: system + vendor + product. */
    fun applySystemPreset() {
        val systemPartitions = currentPartitions
            .filter { it.category == PartitionCategory.LOGICAL_SYSTEM.name && it.isExtractable }
            .map { it.id }
            .toSet()
        _selectedPartitions.value = systemPartitions
        recalculateStorage()
    }

    /** Apply the "Everything extractable" preset. */
    fun applyEverythingPreset() {
        val allExtractable = currentPartitions
            .filter { it.isExtractable }
            .map { it.id }
            .toSet()
        _selectedPartitions.value = allExtractable
        recalculateStorage()
    }

    /** Clear all selections. */
    fun clearSelection() {
        _selectedPartitions.value = emptySet()
        recalculateStorage()
    }

    // =========================================================================
    // Storage calculation
    // =========================================================================

    /**
     * Recalculate storage estimate based on current selection.
     * WHY immediate: FR-6 requires ≤ 16 ms recalculation. This is pure
     * arithmetic — no I/O involved.
     */
    private fun recalculateStorage() {
        val selectedSizes = currentPartitions
            .filter { _selectedPartitions.value.contains(it.id) }
            .map { it.estimatedExtractedSizeBytes }

        val availableBytes = getAvailableStorage()

        _storageEstimate.value = storageEstimateCalculator.calculate(
            selectedPartitionSizes = selectedSizes,
            isIncremental = isIncremental,
            availableBytes = availableBytes,
        )
    }

    /**
     * Query available storage on the default data partition.
     * WHY StatFs: Fast system call — no permission needed, returns
     * actual free bytes on the data partition.
     */
    private fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    // =========================================================================
    // Screen data construction
    // =========================================================================

    private fun buildScreenData(
        pkg: PackageEntity,
        partitions: List<PartitionEntity>,
        tier: SupportTier,
    ): AnalysisScreenData {
        // Group partitions by category
        val grouped = partitions.groupBy { partition ->
            try {
                PartitionCategory.valueOf(partition.category)
            } catch (_: Exception) {
                PartitionCategory.MISC
            }
        }.mapValues { (_, list) ->
            list.sortedBy { it.name }
        }

        // Order groups per PRD FR-6
        val orderedGroups = listOf(
            PartitionCategory.BOOT_CRITICAL,
            PartitionCategory.LOGICAL_SYSTEM,
            PartitionCategory.FIRMWARE_RADIO,
            PartitionCategory.METADATA,
            PartitionCategory.MISC,
        ).mapNotNull { category ->
            grouped[category]?.let { category to it }
        }

        val totalSize = partitions.sumOf { it.estimatedExtractedSizeBytes }

        return AnalysisScreenData(
            packageId = pkg.id,
            displayName = pkg.displayName,
            supportTier = tier,
            classification = pkg.classification,
            isIncremental = pkg.isIncremental,
            packageFamily = pkg.packageFamily,
            securityPatchLevel = pkg.securityPatchLevel,
            deviceModel = pkg.deviceModel,
            fileSizeFormatted = formatFileSize(pkg.fileSizeBytes),
            partitionGroups = orderedGroups,
            totalPartitionCount = partitions.size,
            extractableCount = partitions.count { it.isExtractable },
            totalExtractedSizeFormatted = formatFileSize(totalSize),
            detectedMagicBytes = pkg.detectedMagicBytes,
        )
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
 * Sealed UI state for the analysis screen.
 *
 * Each variant maps to a specific State Matrix entry (PRD §State Matrix,
 * Analysis row).
 */
sealed class AnalysisUiState {
    /** Analysis in progress — "Analyzing file…" with optional phase label. */
    data class Loading(val phase: String) : AnalysisUiState()

    /** Package matches a supported adapter — full analysis with partition selection. */
    data class Supported(val data: AnalysisScreenData) : AnalysisUiState()

    /** Package matches an experimental adapter — analysis with experimental labeling. */
    data class Experimental(val data: AnalysisScreenData) : AnalysisUiState()

    /** Package is forensic-only — inspection, no extraction CTA. */
    data class Forensic(val data: AnalysisScreenData) : AnalysisUiState()

    /** Package is corrupted — show specific error with magic bytes. */
    data class Corrupted(
        val message: String,
        val magicBytes: String?,
    ) : AnalysisUiState()

    /** Format not recognized — show what was detected plus diagnostics CTA. */
    data class FormatUnknown(
        val message: String,
        val magicBytes: String?,
    ) : AnalysisUiState()

    /** DB or system error. */
    data class Error(val message: String) : AnalysisUiState()
}

/**
 * Display data for the analysis screen.
 *
 * Pre-formatted for direct rendering — no business logic in Composables.
 */
@Immutable
data class AnalysisScreenData(
    val packageId: String,
    val displayName: String,
    val supportTier: SupportTier,
    val classification: String,
    val isIncremental: Boolean,
    val packageFamily: String,
    val securityPatchLevel: String?,
    val deviceModel: String?,
    val fileSizeFormatted: String,
    val partitionGroups: List<Pair<PartitionCategory, List<PartitionEntity>>>,
    val totalPartitionCount: Int,
    val extractableCount: Int,
    val totalExtractedSizeFormatted: String,
    val detectedMagicBytes: String?,
)
