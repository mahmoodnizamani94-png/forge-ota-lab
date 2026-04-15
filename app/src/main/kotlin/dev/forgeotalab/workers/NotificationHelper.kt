package dev.forgeotalab.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.forgeotalab.MainActivity
import dev.forgeotalab.R

/**
 * Notification construction and channel management for extraction.
 *
 * PRD §Notification Channel:
 * - Channel ID: forge_extraction_progress
 * - Name: "Extraction Progress"
 * - Importance: IMPORTANCE_LOW (no sound, shows in status bar)
 * - User-configurable: yes (via system settings)
 *
 * WHY IMPORTANCE_LOW: Extraction progress is informational — users should
 * see it in the status bar but not be interrupted by sounds or vibration.
 * The notification persists because it's part of a foreground service.
 */
object NotificationHelper {

    const val CHANNEL_ID = "forge_extraction_progress"
    const val NOTIFICATION_ID = 1001
    const val COMPLETED_NOTIFICATION_ID = 1002

    /**
     * Create the extraction progress notification channel.
     *
     * Must be called from Application.onCreate() before any notification is posted.
     * Safe to call multiple times — Android ignores duplicate channel creation.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Extraction Progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows extraction progress for OTA partition images"
            setShowBadge(false)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Build a progress notification for an active extraction.
     *
     * WHY PendingIntent: Tapping the notification brings the user back
     * to the extraction screen. FLAG_IMMUTABLE per API 31+ requirement.
     *
     * @param context Application context.
     * @param partitionName Name of the partition currently being extracted.
     * @param completed Number of partitions completed.
     * @param total Total number of partitions.
     * @param percent Overall completion percentage (0–100).
     * @return Configured Notification for foreground service.
     */
    fun buildProgressNotification(
        context: Context,
        partitionName: String,
        completed: Int,
        total: Int,
        percent: Int,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Extracting $partitionName")
            .setContentText("$completed/$total partitions · $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tapPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Build a completion notification after extraction finishes.
     *
     * @param context Application context.
     * @param status Terminal job status (COMPLETED, PARTIAL_SUCCESS, FAILED, CANCELED).
     * @param completedCount Number of verified partitions.
     * @param failedCount Number of failed partitions.
     * @return Notification for display after foreground service stops.
     */
    fun buildCompletedNotification(
        context: Context,
        status: String,
        completedCount: Int,
        failedCount: Int,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (title, text) = when (status) {
            "COMPLETED" -> "Extraction Complete" to "$completedCount partition(s) verified"
            "PARTIAL_SUCCESS" -> "Extraction Partial" to
                "$completedCount verified, $failedCount failed"
            "CANCELED" -> "Extraction Canceled" to
                if (completedCount > 0) "$completedCount partition(s) preserved"
                else "No partitions extracted"
            else -> "Extraction Failed" to "All partitions failed"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()
    }

    /**
     * Build a notification for a resumed extraction (FR-9).
     *
     * Shown when the worker restarts after process death. Conveys progress
     * so the user understands extraction is continuing, not starting over.
     *
     * @param context Application context.
     * @param completedSoFar Number of partitions already completed before interruption.
     * @param total Total number of partitions.
     * @return Notification for the resuming foreground service.
     */
    fun buildResumeNotification(
        context: Context,
        completedSoFar: Int,
        total: Int,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Extraction was interrupted. Resuming…")
            .setContentText("$completedSoFar/$total partitions already done")
            .setProgress(total, completedSoFar, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tapPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Build a notification for an interrupted extraction that hasn't been resumed.
     *
     * Shown when the user returns to the app and an interrupted job is detected.
     * Tapping the notification opens the app where the resume banner is visible.
     *
     * @param context Application context.
     * @param completedCount Number of partitions completed before interruption.
     * @param totalCount Total number of partitions in the job.
     * @return Notification prompting the user to resume or clean up.
     */
    fun buildInterruptedNotification(
        context: Context,
        completedCount: Int,
        totalCount: Int,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Extraction interrupted")
            .setContentText(
                "$completedCount/$totalCount partitions completed. Open to resume or clean up.",
            )
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()
    }
}
