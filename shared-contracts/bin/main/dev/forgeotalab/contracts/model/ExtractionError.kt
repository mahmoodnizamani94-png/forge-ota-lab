package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of extraction failure types.
 *
 * WHY sealed class: Exhaustive `when` statements fail to compile if a new error
 * subclass is added without a message mapping — exhaustive by construction,
 * not by discipline. Every variant carries the diagnostic context needed to
 * produce the PRD's required actionable error messages.
 *
 * @see FormatError for analysis-phase failures
 */
@Serializable
sealed class ExtractionError {

    /** Human-readable summary for logging and diagnostics export. */
    abstract val summary: String

    /**
     * Decompression algorithm (XZ, BZ2, ZSTD) failed on a specific data blob.
     * PRD FR-3: mark partition as failed, continue with remaining partitions.
     */
    @Serializable
    data class DecompressFailed(
        val partitionName: String,
        val algorithm: String,
        val dataOffset: Long,
        val expectedBytes: Long,
        val actualBytes: Long,
        override val summary: String = "Decompression failed for partition '$partitionName' " +
            "using $algorithm at offset $dataOffset",
    ) : ExtractionError()

    /**
     * I/O error writing extracted data to destination.
     * PRD FR-3: mark partition as failed with I/O error details, continue.
     */
    @Serializable
    data class WriteFailed(
        val partitionName: String,
        val outputPath: String,
        val ioErrorMessage: String,
        override val summary: String = "Write failed for partition '$partitionName': $ioErrorMessage",
    ) : ExtractionError()

    /**
     * SHA-256 verification failed — extracted output does not match manifest hash.
     * PRD FR-7: partition marked as VERIFICATION_FAILED, excluded from success count.
     */
    @Serializable
    data class VerificationFailed(
        val partitionName: String,
        val expectedHash: String,
        val actualHash: String,
        override val summary: String = "Verification failed for partition '$partitionName': " +
            "expected $expectedHash, got $actualHash",
    ) : ExtractionError()

    /**
     * Decompression bomb detected — output exceeds declared partition size × 1.01.
     * PRD Security: abort partition extraction immediately.
     */
    @Serializable
    data class SizeExceedsLimit(
        val partitionName: String,
        val declaredSizeBytes: Long,
        val actualBytesWritten: Long,
        override val summary: String = "Size limit exceeded for partition '$partitionName': " +
            "declared $declaredSizeBytes bytes, wrote $actualBytesWritten bytes",
    ) : ExtractionError()

    /**
     * SAF URI revoked mid-extraction — source file no longer accessible.
     * PRD Failure #14: extraction stops, preserve completed partitions.
     */
    @Serializable
    data class SourceUriRevoked(
        val uri: String,
        val fileName: String,
        override val summary: String = "Source file '$fileName' is no longer accessible",
    ) : ExtractionError()

    /**
     * Corrupted operation data or blob read failure in the payload.
     * PRD FR-3: retry read once, on second failure mark partition as failed.
     */
    @Serializable
    data class CorruptedOperation(
        val partitionName: String,
        val operationIndex: Int,
        val dataOffset: Long,
        val expectedByteCount: Long,
        val actualByteCount: Long,
        override val summary: String = "Corrupted operation #$operationIndex for partition " +
            "'$partitionName' at offset $dataOffset",
    ) : ExtractionError()

    /**
     * Not enough free space to complete extraction.
     * PRD Failure #4: block job start, show exact deficit.
     */
    @Serializable
    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
        override val summary: String = "Insufficient storage: need ${requiredBytes / 1_048_576} MB, " +
            "${availableBytes / 1_048_576} MB available",
    ) : ExtractionError()

    /**
     * Adapter signaled a job-wide abort — all extraction must stop.
     * PRD FR-3: preserve any verified outputs.
     */
    @Serializable
    data class AdapterAbort(
        val adapterId: String,
        val reason: String,
        override val summary: String = "Adapter '$adapterId' aborted extraction: $reason",
    ) : ExtractionError()
}
