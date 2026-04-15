package dev.forgeotalab.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scoped temporary workspaces for extraction jobs.
 *
 * WHY: Each extraction job needs a temp directory for intermediate files
 * (e.g., extracted partition before SAF copy, partial downloads). Scoped
 * directories prevent cross-job contamination and enable per-job cleanup.
 *
 * Workspace layout:
 *   app_cache/extraction_workspaces/{jobId}/
 *     boot.img.tmp
 *     system.img.tmp
 *     ...
 *
 * Cleanup policy:
 * - Completed/canceled jobs: cleaned immediately after terminal state
 * - Stale workspaces: cleaned if older than 7 days (defensive, for crash recovery)
 */
@Singleton
class WorkspaceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val WORKSPACE_DIR = "extraction_workspaces"

        /** Stale workspace threshold: 7 days in milliseconds. */
        private const val STALE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000
    }

    /**
     * Create a scoped workspace directory for a job.
     *
     * @param jobId UUID of the extraction job.
     * @return File pointing to the job's workspace directory (created).
     */
    fun createJobWorkspace(jobId: String): File {
        val workspace = File(getWorkspaceRoot(), jobId)
        workspace.mkdirs()
        return workspace
    }

    /**
     * Get the workspace directory for a job (may not exist yet).
     */
    fun getJobWorkspace(jobId: String): File {
        return File(getWorkspaceRoot(), jobId)
    }

    /**
     * Create a temp file for a partition within a job's workspace.
     *
     * @param jobId UUID of the extraction job.
     * @param partitionName Name of the partition (e.g., "boot", "system").
     * @return File path for the temporary partition file.
     */
    fun createPartitionTempFile(jobId: String, partitionName: String): File {
        val workspace = createJobWorkspace(jobId)
        return File(workspace, "${partitionName}.img.tmp")
    }

    /**
     * Clean up a job's workspace directory and all its contents.
     *
     * @param jobId UUID of the extraction job.
     * @return true if cleanup succeeded.
     */
    fun cleanupJobWorkspace(jobId: String): Boolean {
        val workspace = File(getWorkspaceRoot(), jobId)
        return if (workspace.exists()) {
            workspace.deleteRecursively()
        } else {
            true
        }
    }

    /**
     * Delete a specific temp file (e.g., in-progress partition on cancellation).
     *
     * @param tempFile File to delete.
     * @return true if deleted or didn't exist.
     */
    fun deleteTempFile(tempFile: File): Boolean {
        return if (tempFile.exists()) {
            tempFile.delete()
        } else {
            true
        }
    }

    /**
     * Clean up all workspaces older than the stale threshold.
     *
     * WHY: Defensive cleanup for workspaces that survived process death
     * without proper cleanup. Called periodically from Application.onCreate().
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 7 days).
     * @return Number of stale workspaces cleaned.
     */
    fun cleanupStaleWorkspaces(maxAgeMs: Long = STALE_THRESHOLD_MS): Int {
        val root = getWorkspaceRoot()
        if (!root.exists()) return 0

        val now = System.currentTimeMillis()
        var cleaned = 0

        root.listFiles()?.forEach { workspace ->
            if (workspace.isDirectory && (now - workspace.lastModified()) > maxAgeMs) {
                if (workspace.deleteRecursively()) {
                    cleaned++
                }
            }
        }

        return cleaned
    }

    /**
     * Calculate total disk space used by all extraction workspaces.
     *
     * @return Total bytes used by workspace directories.
     */
    fun getWorkspaceSize(): Long {
        val root = getWorkspaceRoot()
        if (!root.exists()) return 0L

        return root.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Get the root directory for all extraction workspaces.
     */
    private fun getWorkspaceRoot(): File {
        return File(context.cacheDir, WORKSPACE_DIR)
    }
}
