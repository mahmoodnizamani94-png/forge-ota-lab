package dev.forgeotalab.domain

import dev.forgeotalab.contracts.model.ManifestFeatureFlags
import dev.forgeotalab.data.repository.AdapterManifestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central access point for feature flags from the signed adapter manifest.
 *
 * WHY a dedicated manager: Feature flags gate UI visibility (privileged mode
 * toggle), behavior limits (concurrent job count), and feature availability
 * (incremental reconstruction). A single manager prevents scattered flag
 * reads across ViewModels and use cases.
 *
 * PRD Rollout Controls: "Per-family feature flags in signed manifest",
 * "Kill switch for privileged mode", "concurrent_job_limit flag (set to 1 for v1)."
 */
@Singleton
class FeatureFlagManager @Inject constructor(
    private val adapterManifestRepository: AdapterManifestRepository,
) {

    /**
     * Observe feature flags reactively — for UI binding.
     * Emits defaults when no manifest has been applied.
     */
    fun observeFeatureFlags(): Flow<ManifestFeatureFlags> {
        return adapterManifestRepository.observeFeatureFlags()
            .distinctUntilChanged()
    }

    /**
     * Whether the privileged mode toggle should be visible in settings.
     * Default: false (hidden until manifest enables it).
     */
    suspend fun isPrivilegedModeEnabled(): Boolean {
        return adapterManifestRepository.getFeatureFlags().privilegedModeEnabled
    }

    /**
     * Maximum concurrent extraction jobs allowed.
     * PRD v1: Always 1. Future manifests may raise this.
     */
    suspend fun getConcurrentJobLimit(): Int {
        return adapterManifestRepository.getFeatureFlags().concurrentJobLimit
    }

    /**
     * Whether incremental OTA reconstruction is enabled.
     * Default: true. Can be killed remotely via manifest.
     */
    suspend fun isIncrementalReconstructionEnabled(): Boolean {
        return adapterManifestRepository.getFeatureFlags().incrementalReconstructionEnabled
    }
}
