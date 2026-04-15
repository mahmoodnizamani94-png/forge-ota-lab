package dev.forgeotalab.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.ManifestFeatureFlags
import dev.forgeotalab.contracts.model.ThemeMode
import dev.forgeotalab.data.ForgePreferences
import dev.forgeotalab.data.repository.AdapterManifestRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.ManifestRefreshOutcome
import dev.forgeotalab.data.repository.SettingsRepository
import dev.forgeotalab.domain.FeatureFlagManager
import dev.forgeotalab.domain.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * ViewModel for the Settings screen.
 *
 * Manages:
 * - Theme toggle (dark/light/system) — instant live update via DataStore Flow
 * - Workspace cache display and clearing
 * - Crash reporting opt-in toggle
 * - Privileged mode toggle (hidden unless feature flag enabled)
 * - Adapter manifest version and manual refresh
 * - About metadata (app version)
 *
 * State management: StateFlow<SettingsUiState> + collectAsStateWithLifecycle.
 * No LiveData. No mutable state in Composables.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val forgePreferences: ForgePreferences,
    private val settingsRepository: SettingsRepository,
    private val adapterManifestRepository: AdapterManifestRepository,
    private val featureFlagManager: FeatureFlagManager,
    private val workspaceManager: WorkspaceManager,
    @Named("appVersion") private val appVersion: String,
) : ViewModel() {

    /**
     * Loading state for manifest refresh — drives progress indicator.
     */
    private val _isRefreshingManifest = MutableStateFlow(false)

    /**
     * Message state for one-shot feedback (e.g., "Cache cleared", "Manifest updated").
     */
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    /**
     * Workspace cache size — recomputed on demand.
     */
    private val _workspaceCacheBytes = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            _workspaceCacheBytes.value = workspaceManager.getWorkspaceSize()
        }
    }

    /**
     * Combined UI state — all settings data in a single observable.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        forgePreferences.themeMode,
        forgePreferences.crashReportingEnabled,
        forgePreferences.privilegedModeEnabled,
        featureFlagManager.observeFeatureFlags(),
        _workspaceCacheBytes,
    ) { themeMode, crashReporting, privilegedMode, featureFlags, cacheBytes ->
        SettingsUiState(
            themeMode = themeMode,
            crashReportingEnabled = crashReporting,
            privilegedModeEnabled = privilegedMode,
            privilegedModeVisible = featureFlags.privilegedModeEnabled,
            workspaceCacheBytes = cacheBytes,
            appVersion = appVersion,
            isRefreshingManifest = _isRefreshingManifest.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(appVersion = appVersion),
    )

    // =========================================================================
    // Actions
    // =========================================================================

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            forgePreferences.setThemeMode(mode)
        }
    }

    fun toggleCrashReporting(enabled: Boolean) {
        viewModelScope.launch {
            forgePreferences.setCrashReportingEnabled(enabled)
            settingsRepository.recordConsent(
                type = SettingsRepository.CONSENT_CRASH_REPORTING,
                granted = enabled,
                appVersion = appVersion,
            )
        }
    }

    /**
     * Toggle privileged mode — requires external confirmation dialog.
     * Caller must show PrivilegedModeDialog before calling this with enabled = true.
     */
    fun setPrivilegedModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            forgePreferences.setPrivilegedModeEnabled(enabled)
        }
    }

    fun clearWorkspaceCache() {
        viewModelScope.launch {
            workspaceManager.cleanupStaleWorkspaces(maxAgeMs = 0)
            _workspaceCacheBytes.value = workspaceManager.getWorkspaceSize()
            _feedbackMessage.value = "Workspace cache cleared"
        }
    }

    fun refreshManifest() {
        viewModelScope.launch {
            _isRefreshingManifest.value = true
            val result = adapterManifestRepository.refreshManifest()
            _isRefreshingManifest.value = false

            _feedbackMessage.value = when (result) {
                is DataResult.Success -> {
                    when (val outcome = result.data) {
                        is ManifestRefreshOutcome.Applied ->
                            "Manifest updated: ${outcome.adaptersUpdated} adapters"
                        is ManifestRefreshOutcome.PinnedLastKnownGood ->
                            "Could not reach server. Using cached manifest."
                        is ManifestRefreshOutcome.SignatureFailed ->
                            "Manifest signature verification failed. Using cached version."
                        is ManifestRefreshOutcome.NoManifestAvailable ->
                            "No manifest available."
                    }
                }
                is DataResult.Error -> "Refresh failed: ${result.message}"
            }
        }
    }

    fun consumeFeedbackMessage() {
        _feedbackMessage.value = null
    }
}

/**
 * UI state for the Settings screen.
 *
 * WHY a data class: Compose recomposition compares state by structural equality.
 * A data class provides correct equals/hashCode by construction. @Stable could
 * be used but data class is simpler for this use case.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val crashReportingEnabled: Boolean = false,
    val privilegedModeEnabled: Boolean = false,
    val privilegedModeVisible: Boolean = false,
    val workspaceCacheBytes: Long = 0L,
    val appVersion: String = "",
    val isRefreshingManifest: Boolean = false,
)
