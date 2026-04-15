package dev.forgeotalab.data.entity

/**
 * Display-oriented data class for the home screen history list.
 *
 * WHY not a Room @Entity: This is a query result type, not a table. It's
 * populated by the repository layer by joining PackageEntity with the latest
 * JobEntity for each package. URI accessibility is checked at the repository
 * layer via ContentResolver — it's a runtime check, not a stored field.
 *
 * WHY denormalized: The home screen renders 100+ items in a LazyColumn.
 * Pre-computing the display model in the repository layer (background
 * thread) avoids per-item computation in the Composable's recomposition.
 */
data class HistoryItem(
    // =========================================================================
    // Package fields
    // =========================================================================

    /** Package ID — used for navigation and deletion. */
    val packageId: String,

    /** User-visible filename for display. */
    val displayName: String,

    /** SupportTier enum name — for badge rendering. */
    val supportTier: String,

    /** PackageClassification enum name. */
    val classification: String,

    /** Total file size in bytes — for formatted display. */
    val fileSizeBytes: Long,

    /** Epoch millis when last opened — for relative time display. */
    val lastOpenedAt: Long,

    /** SAF persistable URI — for accessibility checking. */
    val sourceUri: String,

    /** True if the package is incremental (delta). */
    val isIncremental: Boolean,

    /** Android security patch level from manifest metadata. */
    val securityPatchLevel: String? = null,

    /** True once analysis has successfully completed. */
    val analysisComplete: Boolean = false,

    // =========================================================================
    // Latest job fields — from the most recent job for this package
    // =========================================================================

    /** Latest job ID — for deep link navigation to result screen. */
    val latestJobId: String? = null,

    /** Latest job status — for badge display (COMPLETED, INTERRUPTED, etc.). */
    val latestJobStatus: String? = null,

    /** Completed partition count from latest job — for resume progress hint. */
    val completedPartitions: Int = 0,

    /** Total partition count from latest job. */
    val totalPartitions: Int = 0,

    // =========================================================================
    // Runtime-computed fields — set by repository, not from SQL
    // =========================================================================

    /**
     * Whether the source URI is still accessible via ContentResolver.
     *
     * WHY runtime: SAF permissions can be revoked at any time by the user
     * or the system. This must be checked live, not stored — a stored value
     * would be stale by the next launch. Checked in the repository layer
     * on a background thread to avoid blocking the UI.
     */
    val isUriAccessible: Boolean = true,
)
