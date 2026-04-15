package dev.forgeotalab.ui.screens.incremental

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.data.entity.BaseMatchEntity
import dev.forgeotalab.data.repository.BaseImageRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.data.repository.SettingsRepository
import dev.forgeotalab.domain.BaseCacheManager
import dev.forgeotalab.domain.ValidateBaseImageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the incremental prerequisite wizard.
 *
 * Responsibilities:
 * - Load package metadata and prerequisite state from Room
 * - Auto-match prerequisites against the base image cache on init
 * - Validate user-imported base images via SAF with field-level mismatch
 * - Track per-partition validation status and drive CTA enablement
 * - Manage advanced mode raw export toggles
 * - Calculate and display cache usage
 *
 * State management: StateFlow<IncrementalWizardUiState> sealed class. No LiveData.
 *
 * Accessibility: All status text is semantic and complete for TalkBack.
 * Mismatch descriptions include the field name, expected value, and actual value
 * (e.g., "Hash: expected abc123... — found def456...").
 */
@HiltViewModel
class IncrementalWizardViewModel @Inject constructor(
    private val packageRepository: PackageRepository,
    private val baseImageRepository: BaseImageRepository,
    private val validateBaseImageUseCase: ValidateBaseImageUseCase,
    private val baseCacheManager: BaseCacheManager,
    private val settingsRepository: SettingsRepository,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IncrementalWizardUiState>(
        IncrementalWizardUiState.Loading("Loading prerequisite data…"),
    )
    val uiState: StateFlow<IncrementalWizardUiState> = _uiState.asStateFlow()

    private var currentPackageId: String = ""

    /**
     * Initialize the wizard with a package ID.
     *
     * Pipeline:
     * 1. Load package metadata from Room
     * 2. Auto-match prerequisites from cache
     * 3. Start observing prerequisite state reactively
     */
    fun initialize(packageId: String) {
        currentPackageId = packageId
        viewModelScope.launch {
            // Step 1: Load package metadata
            val pkgResult = packageRepository.getPackageById(packageId)
            val pkg = when (pkgResult) {
                is DataResult.Success -> pkgResult.data
                is DataResult.Error -> {
                    _uiState.value = IncrementalWizardUiState.Error(
                        message = "Unable to load package data: ${pkgResult.message}",
                    )
                    return@launch
                }
            }

            if (pkg == null) {
                _uiState.value = IncrementalWizardUiState.Error(
                    message = "Package not found. It may have been removed.",
                )
                return@launch
            }

            // Step 2: Auto-match from cache
            val cacheHits = baseCacheManager.autoMatch(packageId)

            // Step 3: Check advanced mode setting
            val advancedResult = settingsRepository.getSetting(
                SettingsRepository.KEY_ADVANCED_MODE_ENABLED,
            )
            val advancedModeEnabled = when (advancedResult) {
                is DataResult.Success -> advancedResult.data == "true"
                is DataResult.Error -> false
            }

            // Step 4: Start observing prerequisites reactively
            observePrerequisites(packageId, pkg, cacheHits, advancedModeEnabled)
        }
    }

    /**
     * Observe prerequisite state and map to UI state.
     *
     * WHY combine with unmatched: We need both the full list (for rendering cards)
     * and the unmatched count (for CTA enablement). Combining ensures atomic
     * UI updates when a base is validated.
     */
    private fun observePrerequisites(
        packageId: String,
        pkg: dev.forgeotalab.data.entity.PackageEntity,
        cacheHits: Int,
        advancedModeEnabled: Boolean,
    ) {
        viewModelScope.launch {
            combine(
                baseImageRepository.observePrerequisites(packageId),
                baseImageRepository.observeUnmatched(packageId),
            ) { allPrereqs, unmatched ->
                buildWizardData(pkg, allPrereqs, unmatched, cacheHits, advancedModeEnabled)
            }
                .catch { e ->
                    _uiState.value = IncrementalWizardUiState.Error(
                        message = "Unable to load prerequisites: ${e.message}",
                    )
                }
                .collect { wizardData ->
                    _uiState.value = IncrementalWizardUiState.Wizard(data = wizardData)
                }
        }
    }

    /**
     * Called when user taps "Import Base" for a specific partition.
     * The SAF picker result will call validateBase with the selected URI.
     */
    fun validateBase(matchId: String, baseUri: String) {
        viewModelScope.launch {
            // Show validating state
            val current = _uiState.value
            if (current is IncrementalWizardUiState.Wizard) {
                _uiState.value = current.copy(
                    data = current.data.copy(validatingMatchId = matchId),
                )
            }

            val result = validateBaseImageUseCase.execute(matchId, baseUri)

            // Clear validating state — the Flow observation will update the UI
            val afterValidation = _uiState.value
            if (afterValidation is IncrementalWizardUiState.Wizard) {
                _uiState.value = afterValidation.copy(
                    data = afterValidation.data.copy(validatingMatchId = null),
                )
            }
        }
    }

    /**
     * Toggle raw export allowed for a specific partition (advanced mode).
     * PRD FR-5: "unsafe raw export allowed per partition when user explicitly opts in."
     */
    fun toggleRawExport(matchId: String, allowed: Boolean) {
        viewModelScope.launch {
            baseImageRepository.setRawExportAllowed(matchId, allowed)
        }
    }

    // =========================================================================
    // State construction
    // =========================================================================

    private suspend fun buildWizardData(
        pkg: dev.forgeotalab.data.entity.PackageEntity,
        allPrereqs: List<BaseMatchEntity>,
        unmatched: List<BaseMatchEntity>,
        cacheHits: Int,
        advancedModeEnabled: Boolean,
    ): WizardScreenData {
        val prerequisites = allPrereqs.map { match ->
            PrerequisiteItem(
                matchId = match.id,
                partitionName = match.partitionName,
                status = match.matchStatus,
                mismatchField = match.mismatchField,
                mismatchExpected = match.mismatchExpected,
                mismatchActual = match.mismatchActual,
                baseUri = match.matchedBaseUri,
                rawExportAllowed = match.rawExportAllowed,
                requiredFingerprint = match.requiredFingerprint,
                requiredHash = match.requiredHash,
                requiredVersion = match.requiredVersion,
                requiredSlot = match.requiredSlot,
            )
        }

        // CTA is enabled when all partitions are MATCHED or have rawExportAllowed
        val allSatisfied = allPrereqs.all { match ->
            match.matchStatus == "MATCHED" || match.rawExportAllowed
        }

        val unmatchedCount = unmatched.count { !it.rawExportAllowed }

        // Cache usage for display
        val (cacheUsed, cacheCeiling) = baseCacheManager.getCacheUsage()

        return WizardScreenData(
            packageId = pkg.id,
            displayName = pkg.displayName,
            supportTier = SupportTier.EXPERIMENTAL,
            sourceFingerprint = pkg.sourceFingerprint,
            targetFingerprint = pkg.targetFingerprint,
            prerequisites = prerequisites,
            allSatisfied = allSatisfied,
            unmatchedCount = unmatchedCount,
            cachedMatchCount = cacheHits,
            totalPrerequisiteCount = allPrereqs.size,
            advancedModeEnabled = advancedModeEnabled,
            cacheUsedFormatted = baseCacheManager.formatBytes(cacheUsed),
            cacheCeilingFormatted = baseCacheManager.formatBytes(cacheCeiling),
            cacheUsedBytes = cacheUsed,
            cacheCeilingBytes = cacheCeiling,
            validatingMatchId = null,
        )
    }
}

// =============================================================================
// Sealed UI State
// =============================================================================

/**
 * Sealed UI state for the incremental prerequisite wizard.
 *
 * Maps to PRD State Matrix, Incremental wizard row:
 * blocked → importing-base → cached-base-found → validating → ready → mismatch
 */
sealed class IncrementalWizardUiState {
    /** Loading package data and checking cache. */
    data class Loading(val message: String) : IncrementalWizardUiState()

    /** Main wizard state with per-partition prerequisite status. */
    data class Wizard(val data: WizardScreenData) : IncrementalWizardUiState()

    /** Unrecoverable error. */
    data class Error(val message: String) : IncrementalWizardUiState()
}

// =============================================================================
// Display data models
// =============================================================================

/**
 * Pre-formatted display data for the wizard screen.
 * All business logic resolved — Composable only renders.
 */
@Stable
data class WizardScreenData(
    val packageId: String,
    val displayName: String,
    val supportTier: SupportTier,
    val sourceFingerprint: String?,
    val targetFingerprint: String?,
    val prerequisites: List<PrerequisiteItem>,
    val allSatisfied: Boolean,
    val unmatchedCount: Int,
    val cachedMatchCount: Int,
    val totalPrerequisiteCount: Int,
    val advancedModeEnabled: Boolean,
    val cacheUsedFormatted: String,
    val cacheCeilingFormatted: String,
    val cacheUsedBytes: Long,
    val cacheCeilingBytes: Long,
    /** Non-null while a base image is being validated. */
    val validatingMatchId: String?,
)

/**
 * Per-partition prerequisite status for display in the wizard.
 */
@Immutable
data class PrerequisiteItem(
    val matchId: String,
    val partitionName: String,
    /** "MATCHED", "MISMATCHED", or "MISSING". */
    val status: String,
    /** The specific field that mismatched (e.g., "HASH", "FINGERPRINT"). */
    val mismatchField: String?,
    /** The expected value for the mismatched field. */
    val mismatchExpected: String?,
    /** The actual value found in the provided base. */
    val mismatchActual: String?,
    /** SAF URI of the matched base image, if any. */
    val baseUri: String?,
    /** Whether raw unverified export is allowed for this partition. */
    val rawExportAllowed: Boolean,
    /** Required source fingerprint for display. */
    val requiredFingerprint: String?,
    /** Required source hash for display. */
    val requiredHash: String?,
    /** Required source version for display. */
    val requiredVersion: String?,
    /** Required slot for display. */
    val requiredSlot: String?,
) {
    /**
     * Human-readable mismatch description for the UI.
     *
     * Format: "Fingerprint: expected `google/raven/...Mar 2026` — found `google/raven/...Feb 2026`"
     * This is the key UX goal — ROM maintainers understand exactly what went wrong.
     */
    val mismatchDescription: String?
        get() {
            if (mismatchField == null || mismatchExpected == null || mismatchActual == null) {
                return null
            }
            val fieldLabel = when (mismatchField) {
                "FINGERPRINT" -> "Fingerprint"
                "HASH" -> "SHA-256 Hash"
                "VERSION" -> "Version"
                "PARTITION_IDENTITY" -> "Partition Name"
                "SLOT" -> "Slot"
                else -> mismatchField
            }
            return "$fieldLabel: expected $mismatchExpected — found $mismatchActual"
        }
}
