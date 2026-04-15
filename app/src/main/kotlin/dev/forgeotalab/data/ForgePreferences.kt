package dev.forgeotalab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.forgeotalab.contracts.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension property that creates a singleton DataStore instance.
 *
 * WHY DataStore over SharedPreferences: The user spec explicitly mandates
 * DataStore. DataStore provides Flow-based reactive reads, atomic writes,
 * and type safety — critical for instant theme switching without restart.
 *
 * WHY alongside Room SettingsEntity: Room's key-value SettingsEntity stores
 * server-controlled settings (privileged mode, feature flags). DataStore
 * stores user-facing preferences that need instant reactive binding
 * (theme, onboarding, consent).
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "forge_preferences",
)

/**
 * DataStore-backed reactive preferences for user-facing configuration.
 *
 * Covers:
 * - Theme mode (dark/light/system) — instant live update
 * - Crash reporting opt-in — default OFF per PRD
 * - Telemetry consent — default OFF per PRD
 * - Onboarding completion — never re-shown after first run
 * - Last known-good manifest JSON — persisted for offline resilience
 */
@Singleton
class ForgePreferences @Inject constructor(
    private val context: Context,
) {

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // =========================================================================
    // Keys
    // =========================================================================

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val TELEMETRY_CONSENT_GRANTED = booleanPreferencesKey("telemetry_consent_granted")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_KNOWN_GOOD_MANIFEST = stringPreferencesKey("last_known_good_manifest")
        val PRIVILEGED_MODE_ENABLED = booleanPreferencesKey("privileged_mode_enabled")
    }

    // =========================================================================
    // Theme
    // =========================================================================

    /**
     * Observe theme mode reactively — drives ForgeTheme(useDarkTheme = ...).
     * Default: DARK per PRD ("Dark mode as default").
     */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val name = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        try {
            ThemeMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.DARK
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    // =========================================================================
    // Crash Reporting
    // =========================================================================

    /**
     * Crash reporting opt-in state.
     * PRD: "Crash reporting toggle: off by default, opt-in."
     */
    val crashReportingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CRASH_REPORTING_ENABLED] ?: false
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CRASH_REPORTING_ENABLED] = enabled
        }
    }

    // =========================================================================
    // Telemetry Consent
    // =========================================================================

    /**
     * Telemetry consent — recorded during onboarding.
     * PRD: "Telemetry and crash reporting must be opt-in during first-run onboarding."
     */
    val telemetryConsentGranted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TELEMETRY_CONSENT_GRANTED] ?: false
    }

    suspend fun setTelemetryConsentGranted(granted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_CONSENT_GRANTED] = granted
        }
    }

    // =========================================================================
    // Onboarding
    // =========================================================================

    /**
     * Whether onboarding has been completed. Once true, never shown again.
     * PRD: "First launch only [...] Never re-shown. Persisted via DataStore."
     */
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    // =========================================================================
    // Privileged Mode
    // =========================================================================

    /**
     * Privileged mode toggle state.
     * Visibility is controlled by FeatureFlagManager.isPrivilegedModeEnabled().
     * This only stores whether the user has enabled it when visible.
     */
    val privilegedModeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PRIVILEGED_MODE_ENABLED] ?: false
    }

    suspend fun setPrivilegedModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PRIVILEGED_MODE_ENABLED] = enabled
        }
    }

    // =========================================================================
    // Manifest Persistence
    // =========================================================================

    /**
     * Last known-good manifest JSON — pinned for offline resilience.
     * If manifest refresh fails or signature verification fails, this
     * version continues to be used.
     */
    val lastKnownGoodManifest: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_KNOWN_GOOD_MANIFEST]
    }

    suspend fun setLastKnownGoodManifest(manifestJson: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_GOOD_MANIFEST] = manifestJson
        }
    }
}
