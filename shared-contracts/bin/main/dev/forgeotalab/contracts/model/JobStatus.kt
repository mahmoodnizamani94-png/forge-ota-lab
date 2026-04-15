package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Lifecycle states for an extraction job.
 *
 * WHY this state set: Matches the PRD State Matrix extraction row. The state
 * machine is linear with branching terminal states. PARTIAL_SUCCESS is first-class
 * per PRD Non-Negotiable Rule — if 5 of 7 partitions succeed, that is partial
 * success, not failure.
 */
@Serializable
enum class JobStatus {
    /** Job created but not yet started — waiting for preflight or queue position. */
    QUEUED,

    /** Actively extracting partitions. */
    RUNNING,

    /** User-initiated pause or system-initiated background pause. */
    PAUSED,

    /**
     * Process died while job was RUNNING — detected on next app launch.
     *
     * WHY a separate state: A RUNNING job after restart is ambiguous — it could
     * mean "currently executing" or "was killed mid-flight." INTERRUPTED removes
     * that ambiguity. The ExtractionWorker marks stale RUNNING jobs as INTERRUPTED
     * on startup, and the resume flow acts on this status specifically.
     *
     * User sees: "Extraction was interrupted. Resume or clean up."
     */
    INTERRUPTED,

    /** Some partitions verified, others failed. Verified outputs remain available. */
    PARTIAL_SUCCESS,

    /** All selected partitions extracted and verified. */
    COMPLETED,

    /** All partitions failed or an adapter-level abort occurred. */
    FAILED,

    /** User canceled the job. Verified outputs preserved, temp files cleaned. */
    CANCELED,
}
