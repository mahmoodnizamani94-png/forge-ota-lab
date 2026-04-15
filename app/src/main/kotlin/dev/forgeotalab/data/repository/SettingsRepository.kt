package dev.forgeotalab.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings and consent management.
 */
interface SettingsRepository {

    /**
     * Observe a setting value reactively — for theme toggle, privileged mode, etc.
     */
    fun observeSetting(key: String): Flow<String?>

    /**
     * Set a setting value. Creates or updates the entry.
     */
    suspend fun setSetting(key: String, value: String): DataResult<Unit>

    /**
     * One-shot read of a setting value.
     */
    suspend fun getSetting(key: String): DataResult<String?>

    /**
     * Check if consent was granted for a specific type (TELEMETRY, CRASH_REPORTING).
     */
    suspend fun isConsentGranted(type: String): DataResult<Boolean>

    /**
     * Record a consent decision. Append-only for audit trail.
     */
    suspend fun recordConsent(type: String, granted: Boolean, appVersion: String): DataResult<Unit>

    companion object {
        // Standard setting keys — new keys can be added without schema migration.
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PRIVILEGED_MODE_ENABLED = "privileged_mode_enabled"
        const val KEY_BASE_CACHE_CEILING_BYTES = "base_cache_ceiling_bytes"
        const val KEY_ADVANCED_MODE_ENABLED = "advanced_mode_enabled"

        // Consent types
        const val CONSENT_TELEMETRY = "TELEMETRY"
        const val CONSENT_CRASH_REPORTING = "CRASH_REPORTING"
    }
}
