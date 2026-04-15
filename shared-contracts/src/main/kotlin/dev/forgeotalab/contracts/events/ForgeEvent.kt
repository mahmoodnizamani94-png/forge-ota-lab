package dev.forgeotalab.contracts.events

import kotlinx.serialization.Serializable

/**
 * Structured event definitions for instrumentation.
 *
 * WHY: The PRD requires every phase transition and failure class to emit
 * structured events. These event types are shared between the Kotlin app
 * (which logs them) and the Rust core (which produces them via JNI).
 *
 * This file establishes the pattern — individual event classes are added
 * as features land, matching the PRD's Required Events list.
 */
@Serializable
sealed class ForgeEvent {

    /** Timestamp in epoch milliseconds — set by the emitter. */
    abstract val timestampMs: Long

    @Serializable
    data class AnalysisStarted(
        override val timestampMs: Long,
        val packageSizeBytes: Long,
    ) : ForgeEvent()

    @Serializable
    data class AnalysisCompleted(
        override val timestampMs: Long,
        val format: String,
        val partitionCount: Int,
        val otaType: String,
        val parseDurationMs: Long,
    ) : ForgeEvent()

    @Serializable
    data class AnalysisFailed(
        override val timestampMs: Long,
        val errorType: String,
        val detectedMagicBytes: String? = null,
    ) : ForgeEvent()

    // =========================================================================
    // Extraction events — PRD §Required Events
    // =========================================================================

    @Serializable
    data class ExtractionStarted(
        override val timestampMs: Long,
        val jobId: String,
        val packageId: String,
        val partitionCount: Int,
        val supportTier: String,
    ) : ForgeEvent()

    @Serializable
    data class ExtractionPhaseChanged(
        override val timestampMs: Long,
        val jobId: String,
        val partitionName: String,
        val phase: String,
        val progressPercent: Int,
    ) : ForgeEvent()

    @Serializable
    data class ExtractionCompleted(
        override val timestampMs: Long,
        val jobId: String,
        val status: String,
        val completedCount: Int,
        val failedCount: Int,
        val durationMs: Long,
    ) : ForgeEvent()

    @Serializable
    data class PartitionExtracted(
        override val timestampMs: Long,
        val jobId: String,
        val partitionName: String,
        val bytesExtracted: Long,
        val durationMs: Long,
        val decompressAlgorithm: String? = null,
    ) : ForgeEvent()

    @Serializable
    data class ArtifactVerified(
        override val timestampMs: Long,
        val jobId: String,
        val partitionName: String,
        val status: String,
        val durationMs: Long,
    ) : ForgeEvent()

    @Serializable
    data class ArtifactVerifyFailed(
        override val timestampMs: Long,
        val jobId: String,
        val partitionName: String,
        val expectedHash: String,
        val actualHash: String,
    ) : ForgeEvent()

    @Serializable
    data class ConcurrentJobBlocked(
        override val timestampMs: Long,
        val existingJobId: String,
    ) : ForgeEvent()

    // =========================================================================
    // Incremental prerequisite events — PRD §Required Events
    // =========================================================================

    /**
     * Emitted when an incremental package's extraction CTA is blocked
     * because not all selected partitions have validated bases.
     */
    @Serializable
    data class IncrementalPrereqBlocked(
        override val timestampMs: Long,
        val packageId: String,
        val missingCount: Int,
    ) : ForgeEvent()

    /**
     * Emitted when all selected partitions for an incremental package
     * have validated bases — extraction CTA becomes enabled.
     */
    @Serializable
    data class IncrementalPrereqSatisfied(
        override val timestampMs: Long,
        val packageId: String,
        val partitionCount: Int,
    ) : ForgeEvent()

    /**
     * Emitted when a base image fails validation for a specific field.
     * The mismatchField drives G3 (incremental clarity rate) diagnostics.
     */
    @Serializable
    data class IncrementalBaseMismatch(
        override val timestampMs: Long,
        val packageId: String,
        val partitionName: String,
        val mismatchField: String,
    ) : ForgeEvent()

    // =========================================================================
    // Filesystem browser events — PRD FR-8 §Required Events
    // =========================================================================

    /**
     * Emitted when a user opens the filesystem browser for a verified artifact.
     * Tracks browse frequency and filesystem type distribution.
     */
    @Serializable
    data class FilesystemBrowserOpened(
        override val timestampMs: Long,
        val artifactId: String,
        val filesystemType: String,
    ) : ForgeEvent()

    /**
     * Emitted when a file or directory is exported from the filesystem browser.
     * Tracks export frequency and volume.
     */
    @Serializable
    data class FilesystemExported(
        override val timestampMs: Long,
        val artifactId: String,
        val fileCount: Int,
        val totalBytes: Long,
    ) : ForgeEvent()

    /**
     * Emitted when an unsupported filesystem is encountered.
     * Tracks demand for new filesystem format support.
     */
    @Serializable
    data class FilesystemUnsupported(
        override val timestampMs: Long,
        val artifactId: String,
        val detectedFormat: String,
    ) : ForgeEvent()

    // =========================================================================
    // Job persistence and resumability events — PRD FR-9 §Required Events
    // =========================================================================

    /**
     * Emitted when a job resumes after process death or interruption.
     * Tracks G6 (resume recovery rate) and resume latency.
     */
    @Serializable
    data class JobResumedAfterInterruption(
        override val timestampMs: Long,
        val jobId: String,
        val resumeCount: Int,
        val skippedPartitions: Int,
        val resumeLatencyMs: Long,
    ) : ForgeEvent()

    /**
     * Emitted when a job cannot be recovered after interruption.
     * Failure reason drives diagnostics for G6 guardrail.
     */
    @Serializable
    data class JobRecoveryFailed(
        override val timestampMs: Long,
        val jobId: String,
        val reason: String,
    ) : ForgeEvent()

    /**
     * Emitted when an interrupted job's workspace is cleaned up.
     * Tracks how many verified outputs were preserved.
     */
    @Serializable
    data class JobCleanupCompleted(
        override val timestampMs: Long,
        val jobId: String,
        val preservedArtifactCount: Int,
        val cleanedTempBytes: Long,
    ) : ForgeEvent()

    // =========================================================================
    // History events — PRD FR-11 §Required Events
    // =========================================================================

    /**
     * Emitted when the history list is opened on the home screen.
     * Tracks history engagement frequency.
     */
    @Serializable
    data class HistoryOpened(
        override val timestampMs: Long,
        val entryCount: Int,
    ) : ForgeEvent()

    /**
     * Emitted when a history re-open fails (e.g., revoked URI).
     * Tracks URI revocation frequency for UX health.
     */
    @Serializable
    data class HistoryReopenFailed(
        override val timestampMs: Long,
        val packageId: String,
        val reason: String,
    ) : ForgeEvent()

    /**
     * Emitted when a history item is removed (swipe-to-delete or auto-purge).
     * The source distinguishes user action from automatic retention enforcement.
     */
    @Serializable
    data class HistoryItemRemoved(
        override val timestampMs: Long,
        val packageId: String,
        val source: String,
    ) : ForgeEvent()

    // =========================================================================
    // Diagnostics events — PRD FR-10, FR-12 §Required Events
    // =========================================================================

    /**
     * Emitted when a diagnostics bundle is exported for a failed or partial job.
     * Tracks G7 (unsupported-to-diagnostics conversion rate).
     */
    @Serializable
    data class DiagnosticsExported(
        override val timestampMs: Long,
        val jobId: String? = null,
        val packageId: String,
        val supportTier: String,
        val bundleSizeBytes: Long,
    ) : ForgeEvent()

    /**
     * Emitted when a format report is exported for any analyzed package.
     */
    @Serializable
    data class FormatReportExported(
        override val timestampMs: Long,
        val packageId: String,
        val supportTier: String,
    ) : ForgeEvent()

    // =========================================================================
    // Adapter manifest events — PRD FR-2 §Required Events
    // =========================================================================

    /**
     * Emitted when the adapter registry is loaded on app startup.
     */
    @Serializable
    data class AdapterRegistryLoaded(
        override val timestampMs: Long,
        val adapterCount: Int,
        val revokedCount: Int,
    ) : ForgeEvent()

    /**
     * Emitted when the adapter manifest is successfully refreshed from the server.
     */
    @Serializable
    data class AdapterManifestRefreshed(
        override val timestampMs: Long,
        val manifestVersion: Int,
        val adapterCount: Int,
        val revocationCount: Int,
    ) : ForgeEvent()

    /**
     * Emitted when manifest refresh fails (timeout, network error).
     * App pins last known-good manifest silently.
     */
    @Serializable
    data class AdapterRegistryRefreshFailed(
        override val timestampMs: Long,
        val reason: String,
    ) : ForgeEvent()

    /**
     * Emitted when manifest Ed25519 signature validation fails.
     * This is a hard fail — previous version is pinned, suspicious
     * manifest is rejected entirely.
     */
    @Serializable
    data class AdapterManifestSignatureFailed(
        override val timestampMs: Long,
        val reason: String,
    ) : ForgeEvent()

    /**
     * Emitted when an adapter version is revoked via manifest update.
     * Revoked adapters are removed from active registry immediately.
     */
    @Serializable
    data class AdapterRevocationApplied(
        override val timestampMs: Long,
        val adapterId: String,
    ) : ForgeEvent()
}

