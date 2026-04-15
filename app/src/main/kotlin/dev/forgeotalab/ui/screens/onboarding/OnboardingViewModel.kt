package dev.forgeotalab.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.data.ForgePreferences
import dev.forgeotalab.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * ViewModel for the first-launch onboarding flow.
 *
 * Manages:
 * - Current page index (0, 1, 2)
 * - Telemetry consent toggle state (default OFF per PRD)
 * - Completion action — records consent + marks onboarding done in DataStore
 * - Skip action — same as complete but preserves default consent (OFF)
 *
 * PRD: "Skippable. Never re-shown. Persisted via DataStore."
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val forgePreferences: ForgePreferences,
    private val settingsRepository: SettingsRepository,
    @Named("appVersion") private val appVersion: String,
) : ViewModel() {

    private val _telemetryConsent = MutableStateFlow(false)
    val telemetryConsent: StateFlow<Boolean> = _telemetryConsent.asStateFlow()

    fun setTelemetryConsent(granted: Boolean) {
        _telemetryConsent.value = granted
    }

    /**
     * Complete onboarding — records consent decisions and marks onboarding done.
     * After this, onboarding is never shown again.
     */
    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            val consent = _telemetryConsent.value

            // Record telemetry consent decision in Room (audit trail)
            settingsRepository.recordConsent(
                type = SettingsRepository.CONSENT_TELEMETRY,
                granted = consent,
                appVersion = appVersion,
            )

            // Persist consent state to DataStore for reactive binding
            forgePreferences.setTelemetryConsentGranted(consent)

            // If telemetry is granted, also enable crash reporting by default
            if (consent) {
                forgePreferences.setCrashReportingEnabled(true)
                settingsRepository.recordConsent(
                    type = SettingsRepository.CONSENT_CRASH_REPORTING,
                    granted = true,
                    appVersion = appVersion,
                )
            }

            // Mark onboarding as completed — never shown again
            forgePreferences.setOnboardingCompleted()

            onComplete()
        }
    }

    /**
     * Skip onboarding — marks complete with default consent values (all OFF).
     */
    fun skipOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Record explicit skip with default OFF
            settingsRepository.recordConsent(
                type = SettingsRepository.CONSENT_TELEMETRY,
                granted = false,
                appVersion = appVersion,
            )

            forgePreferences.setOnboardingCompleted()
            onComplete()
        }
    }
}
