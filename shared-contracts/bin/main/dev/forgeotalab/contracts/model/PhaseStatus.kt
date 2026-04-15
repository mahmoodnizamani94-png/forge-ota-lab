package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Progress status for an individual job phase.
 *
 * WHY SKIPPED exists: Not all partitions go through every phase. For example,
 * RECONSTRUCT is skipped for full OTA partitions. Tracking skips prevents
 * the UI from showing stale "pending" status for inapplicable phases.
 */
@Serializable
enum class PhaseStatus {
    /** Phase not yet started. */
    PENDING,

    /** Phase currently executing. */
    RUNNING,

    /** Phase finished successfully. */
    COMPLETED,

    /** Phase failed with error details recorded. */
    FAILED,

    /** Phase not applicable for this partition/job configuration. */
    SKIPPED,
}
