package dev.forgeotalab.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.forgeotalab.data.ForgePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consent-gated Crashlytics wrapper.
 *
 * PRD §Permissions: "Telemetry and crash reporting must be opt-in during
 * first-run onboarding." This class enforces that contract:
 *
 * 1. Crashlytics collection is disabled immediately on initialize()
 *    (belt-and-suspenders with the manifest meta-data flag).
 * 2. The consent preference is observed via Flow.
 * 3. Collection is enabled ONLY when the user explicitly opts in.
 * 4. Consent revocation takes effect immediately.
 *
 * If Firebase is not configured (no google-services.json), all calls
 * fail gracefully — the app functions identically without crash reporting.
 *
 * WHY singleton: Crashlytics state is global. Multiple instances observing
 * the consent flow would cause redundant enable/disable toggles.
 */
@Singleton
class CrashReportingManager @Inject constructor(
    private val preferences: ForgePreferences,
) {

    /**
     * Dedicated scope for consent observation.
     *
     * WHY SupervisorJob: If consent observation fails (shouldn't happen),
     * it must not take down the application scope.
     * WHY Dispatchers.IO: DataStore reads happen on IO.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize crash reporting in the disabled state and begin
     * observing the consent preference.
     *
     * Must be called from Application.onCreate() BEFORE any other
     * code that could throw — ensures no crash data is captured
     * without consent.
     */
    fun initialize() {
        try {
            // Disable collection immediately. This is the first thing that
            // happens, ensuring no crash data leaks before consent check.
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)

            // Observe consent changes — enable only when user opts in.
            // distinctUntilChanged avoids redundant Crashlytics API calls.
            scope.launch {
                preferences.crashReportingEnabled
                    .distinctUntilChanged()
                    .collect { enabled ->
                        FirebaseCrashlytics.getInstance()
                            .setCrashlyticsCollectionEnabled(enabled)
                    }
            }
        } catch (_: Exception) {
            // Firebase not configured (no google-services.json) or
            // initialization failed. This is expected during development
            // and in builds distributed without Firebase. The app
            // continues normally without crash reporting.
        }
    }

    /**
     * Log a non-fatal exception to Crashlytics if collection is enabled.
     * No-op if Crashlytics is not initialized or consent not given.
     */
    fun recordException(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (_: Exception) {
            // Firebase not available — silently ignore.
        }
    }

    /**
     * Set a custom key for crash report context.
     * No-op if Crashlytics is not initialized.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (_: Exception) {
            // Firebase not available — silently ignore.
        }
    }
}
