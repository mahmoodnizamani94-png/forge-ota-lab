package dev.forgeotalab.domain

import dev.forgeotalab.contracts.model.StorageEstimate
import javax.inject.Inject

/**
 * Pure calculation for extraction storage estimates.
 *
 * WHY a dedicated class: FR-6 requires instant recalculation when partition
 * selection changes (≤ 16 ms). All math is pure — no I/O, no coroutines.
 * This makes it trivially testable and fast enough for recomposition.
 *
 * Storage budget formula (PRD NFR-6):
 * - Full OTA: selected outputs × 1.25 overhead × 1.02 filesystem overhead
 * - Incremental: selected outputs × 1.40 overhead × 1.02 filesystem overhead
 */
class StorageEstimateCalculator @Inject constructor() {

    companion object {
        /** Full OTA overhead factor per PRD NFR-6. */
        const val FULL_OTA_OVERHEAD = 1.25

        /** Incremental reconstruction overhead factor per PRD NFR-6. */
        const val INCREMENTAL_OVERHEAD = 1.40

        /** Filesystem overhead per PRD FR-6. */
        const val FS_OVERHEAD = 1.02
    }

    /**
     * Calculate the storage estimate for a set of selected partitions.
     *
     * @param selectedPartitionSizes List of target sizes for selected partitions.
     * @param isIncremental Whether this is an incremental package.
     * @param availableBytes Free space on the target storage volume.
     * @return StorageEstimate with required bytes, surplus/deficit, and flags.
     */
    fun calculate(
        selectedPartitionSizes: List<Long>,
        isIncremental: Boolean,
        availableBytes: Long,
    ): StorageEstimate {
        val rawTotal = selectedPartitionSizes.sum()
        val overheadFactor = if (isIncremental) INCREMENTAL_OVERHEAD else FULL_OTA_OVERHEAD
        val requiredBytes = (rawTotal * overheadFactor * FS_OVERHEAD).toLong()

        return StorageEstimate(
            requiredBytes = requiredBytes,
            availableBytes = availableBytes,
            overheadFactor = overheadFactor,
            filesystemOverheadFactor = FS_OVERHEAD,
        )
    }
}
