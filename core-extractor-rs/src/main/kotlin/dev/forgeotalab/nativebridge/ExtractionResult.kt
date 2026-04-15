package dev.forgeotalab.nativebridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed extraction result parsed from the JNI JSON envelope.
 *
 * WHY sealed: Enables exhaustive `when` handling in the ExtractionWorker.
 * New result types cause compile errors at every consumption site.
 */
sealed class ExtractionResult {

    /**
     * Extraction succeeded — partition extracted with SHA-256 hash.
     */
    data class Success(val outcome: NativeExtractionOutcome) : ExtractionResult()

    /**
     * Extraction failed — carries error code and message from Rust core.
     */
    data class Error(
        val code: String,
        val message: String,
        val details: String? = null,
    ) : ExtractionResult()
}

/**
 * Deserialized extraction outcome from the Rust core.
 *
 * Field naming matches Rust's serde snake_case serialization.
 */
@Serializable
data class NativeExtractionOutcome(
    /** Partition name that was extracted. */
    val partition: String,

    /** Total bytes extracted. */
    @SerialName("bytes_extracted")
    val bytesExtracted: Long,

    /** SHA-256 hex digest of the extracted output. */
    val sha256: String,

    /** Number of InstallOperations executed. */
    @SerialName("operations_executed")
    val operationsExecuted: Int,
)
