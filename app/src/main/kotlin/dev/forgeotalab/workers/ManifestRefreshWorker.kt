package dev.forgeotalab.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.forgeotalab.data.repository.AdapterManifestRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.ManifestRefreshOutcome

/**
 * WorkManager periodic worker for adapter manifest refresh.
 *
 * PRD FR-2: "Periodic refresh via WorkManager (24h, NetworkType.CONNECTED)."
 *
 * WHY periodic (not one-shot): Adapter coverage expands through signed manifests.
 * Users who don't open the app daily still get updated adapter registries
 * when they eventually launch.
 *
 * WHY constrained to CONNECTED: Manifest refresh requires network. Running
 * without network wastes battery and always results in pinned-last-known-good.
 *
 * WHY @HiltWorker: Enables constructor injection of repository dependencies.
 * Without Hilt, workers would need manual DI wiring via WorkerFactory.
 */
@HiltWorker
class ManifestRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val adapterManifestRepository: AdapterManifestRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "manifest_refresh"
    }

    override suspend fun doWork(): Result {
        val result = adapterManifestRepository.refreshManifest()

        return when (result) {
            is DataResult.Success -> {
                when (result.data) {
                    is ManifestRefreshOutcome.Applied -> Result.success()
                    is ManifestRefreshOutcome.PinnedLastKnownGood -> {
                        // Not a failure from WorkManager's perspective —
                        // the app continues with pinned manifest.
                        Result.success()
                    }
                    is ManifestRefreshOutcome.SignatureFailed -> {
                        // Signature failure is not retryable — the manifest
                        // endpoint is serving a bad signature.
                        Result.success()
                    }
                    is ManifestRefreshOutcome.NoManifestAvailable -> {
                        // Retry on next period — maybe network wasn't available.
                        Result.retry()
                    }
                }
            }
            is DataResult.Error -> {
                // Unexpected error — retry on next period.
                Result.retry()
            }
        }
    }
}
