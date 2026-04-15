package dev.forgeotalab.data.repository

import dev.forgeotalab.contracts.model.AdapterManifest
import dev.forgeotalab.contracts.model.ManifestFeatureFlags
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for signed adapter manifest operations.
 *
 * WHY a separate repository: The adapter manifest service has its own
 * failure semantics (signature verification, timeout pinning, revocation)
 * that differ from the general settings or adapter registry operations.
 */
interface AdapterManifestRepository {

    /**
     * Refresh the adapter manifest from the remote endpoint.
     *
     * Sequence:
     * 1. HTTP GET signed manifest envelope
     * 2. Verify Ed25519 signature BEFORE parsing body
     * 3. On success: upsert adapters, apply revocations, persist as last-known-good
     * 4. On timeout: pin last-known-good, log event
     * 5. On signature mismatch: hard fail, pin previous, log event
     *
     * @return Outcome describing what happened.
     */
    suspend fun refreshManifest(): DataResult<ManifestRefreshOutcome>

    /**
     * Observe the feature flags from the last-applied manifest.
     * Returns defaults when no manifest has been applied.
     */
    fun observeFeatureFlags(): Flow<ManifestFeatureFlags>

    /**
     * One-shot read of current feature flags.
     */
    suspend fun getFeatureFlags(): ManifestFeatureFlags

    /**
     * Get the currently applied manifest — null if none has been applied yet.
     */
    suspend fun getCurrentManifest(): AdapterManifest?
}

/**
 * Outcome of a manifest refresh attempt.
 */
sealed class ManifestRefreshOutcome {
    /** Manifest successfully verified and applied. */
    data class Applied(
        val manifest: AdapterManifest,
        val adaptersUpdated: Int,
        val revocationsApplied: Int,
    ) : ManifestRefreshOutcome()

    /** Network timeout or error — last known-good manifest pinned. */
    data class PinnedLastKnownGood(
        val reason: String,
    ) : ManifestRefreshOutcome()

    /** Signature verification failed — previous manifest pinned, this rejected. */
    data class SignatureFailed(
        val reason: String,
    ) : ManifestRefreshOutcome()

    /** No manifest available (first run, no network, no pinned version). */
    data object NoManifestAvailable : ManifestRefreshOutcome()
}
