package dev.forgeotalab.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.PackageRepository

/**
 * Periodic WorkManager task enforcing history retention rules (FR-11).
 *
 * Runs every 24 hours with battery-not-low constraint:
 * 1. Purge packages older than 90 days
 * 2. Purge excess entries beyond the 100-entry limit
 *
 * PRD FR-11: "History limited to last 100 entries. Entries auto-purge after 90 days."
 * PRD: "History retention enforced via periodic WorkManager task."
 *
 * WHY periodic instead of on-every-insert: Insert-time enforcement adds
 * latency to the import path. Periodic enforcement is fire-and-forget —
 * the import completes instantly, and stale entries are cleaned up within
 * 24 hours. A budget tablet in Doze mode still runs periodic WorkManager
 * tasks when the maintenance window opens.
 */
@HiltWorker
class HistoryPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val packageRepository: PackageRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "forge_history_purge"
        private const val TAG = "HistoryPurgeWorker"

        /**
         * FR-11: Entries auto-purge after 90 days.
         */
        const val RETENTION_DAYS = 90

        /**
         * FR-11: History limited to last 100 entries.
         */
        const val MAX_ENTRIES = 100
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting history retention purge")

        var expiredPurged = 0
        var excessPurged = 0

        // Step 1: Purge packages older than 90 days
        when (val expiredResult = packageRepository.purgeExpired(RETENTION_DAYS)) {
            is DataResult.Success -> {
                expiredPurged = expiredResult.data
                if (expiredPurged > 0) {
                    Log.i(TAG, "Purged $expiredPurged expired packages (>$RETENTION_DAYS days)")
                }
            }
            is DataResult.Error -> {
                Log.e(TAG, "Failed to purge expired packages: ${expiredResult.message}")
                // Continue with excess purge — don't fail the entire task
            }
        }

        // Step 2: Purge excess entries beyond 100
        when (val excessResult = packageRepository.purgeExcess(MAX_ENTRIES)) {
            is DataResult.Success -> {
                excessPurged = excessResult.data
                if (excessPurged > 0) {
                    Log.i(TAG, "Purged $excessPurged excess packages (>$MAX_ENTRIES entries)")
                }
            }
            is DataResult.Error -> {
                Log.e(TAG, "Failed to purge excess packages: ${excessResult.message}")
            }
        }

        Log.i(
            TAG,
            "History purge completed: $expiredPurged expired + $excessPurged excess = " +
                "${expiredPurged + excessPurged} total purged",
        )

        return Result.success()
    }
}
