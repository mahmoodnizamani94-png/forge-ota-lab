package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Phases within an extraction job.
 *
 * WHY this ordering: From PRD Workflow A step 9 — users see phase-level progress:
 * scan → validate → reconstruct (if needed) → extract → verify → export.
 * Each phase is tracked independently to support per-partition resume (FR-9).
 */
@Serializable
enum class JobPhaseType {
    /** Initial scan of the package structure and partition inventory. */
    SCAN,

    /** Preflight validation: storage budget, permissions, adapter readiness. */
    VALIDATE,

    /** Reconstruction of partitions from incremental delta operations. */
    RECONSTRUCT,

    /** Streaming extraction of partition data from payload. */
    EXTRACT,

    /** SHA-256 verification against manifest target hashes. */
    VERIFY,

    /** Writing verified artifacts to SAF destination. */
    EXPORT,
}
