package dev.forgeotalab

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.forgeotalab.diagnostics.CrashReportingManager
import dev.forgeotalab.domain.WorkspaceManager
import dev.forgeotalab.workers.HistoryPurgeWorker
import dev.forgeotalab.workers.ManifestRefreshWorker
import dev.forgeotalab.workers.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Forge OTA Lab application class.
 *
 * @HiltAndroidApp triggers Hilt's code generation: component hierarchy,
 * module installation, and dependency provision. This is the single
 * entry point for the DI graph.
 *
 * Implements Configuration.Provider to configure WorkManager with
 * Hilt's HiltWorkerFactory — enabling dependency injection in workers.
 *
 * WHY Configuration.Provider: The default WorkManager initializer doesn't
 * know how to inject Hilt dependencies (DAOs, repositories) into workers.
 * By providing a custom Configuration, WorkManager uses HiltWorkerFactory
 * which handles @AssistedInject constructors.
 */
@HiltAndroidApp
class ForgeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workspaceManager: WorkspaceManager

    @Inject
    lateinit var crashReportingManager: CrashReportingManager

    /**
     * Application-scoped coroutine scope for non-critical background work.
     *
     * WHY SupervisorJob: If one child fails (e.g., workspace cleanup hits a
     * corrupted directory), other children (worker enqueue) should continue.
     * WHY Dispatchers.IO: These operations involve filesystem scanning and
     * WorkManager API calls — neither should block the main thread or delay
     * the first frame during cold start.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize crash reporting FIRST — before any code that could throw.
        // WHY first: Ensures setCrashlyticsCollectionEnabled(false) runs before
        // any potential crash, preventing data leak without consent.
        crashReportingManager.initialize()

        // Create notification channel — must be done before any notification is posted.
        // Safe to call multiple times; Android ignores duplicate channel creation.
        // WHY on main thread: Channel creation is fast and required before any
        // foreground service notification can be posted.
        NotificationHelper.createChannel(this)

        // Defer non-critical startup work to background thread.
        // WHY: These do not need to complete before the first frame renders.
        // Moving them off the main thread reduces cold start latency.
        applicationScope.launch {
            // Clean up stale extraction workspaces from previous sessions.
            // WHY here: Workspaces that survived process death (e.g., OOM kills)
            // won't have been cleaned by the Worker's finally block.
            workspaceManager.cleanupStaleWorkspaces()

            // Enqueue periodic workers
            enqueueHistoryPurgeWorker()
            enqueueManifestRefreshWorker()
        }
    }

    /**
     * Schedule the periodic history retention purge worker (FR-11).
     *
     * Runs every 24 hours. Purges packages older than 90 days and
     * enforces the 100-entry history limit.
     */
    private fun enqueueHistoryPurgeWorker() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val purgeRequest = PeriodicWorkRequestBuilder<HistoryPurgeWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag("history_purge")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HistoryPurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest,
        )
    }

    /**
     * Schedule the periodic adapter manifest refresh worker (FR-2).
     *
     * Runs every 24 hours, constrained to network connectivity.
     *
     * WHY KEEP: If a refresh is already scheduled, don't reset its timer.
     * Users who open the app daily get refreshes from the periodic schedule;
     * additional manual refreshes can be triggered from Settings.
     *
     * WHY NetworkType.CONNECTED: Manifest refresh requires network. Running
     * without network always results in pinned-last-known-good — a no-op.
     */
    private fun enqueueManifestRefreshWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val refreshRequest = PeriodicWorkRequestBuilder<ManifestRefreshWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag("manifest_refresh")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ManifestRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest,
        )
    }
}
