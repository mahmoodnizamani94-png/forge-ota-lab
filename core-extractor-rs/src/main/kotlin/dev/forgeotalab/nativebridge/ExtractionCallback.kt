package dev.forgeotalab.nativebridge

/**
 * Callback interface for extraction progress reporting from the Rust core.
 *
 * WHY an interface: Decouples the JNI boundary from the extraction pipeline.
 * The ExtractionWorker implements this to bridge Rust progress events into
 * Kotlin Flows and Room DB updates. Testing uses a fake implementation.
 *
 * WHY isCancelled() here: The Rust core's cancel_token is an AtomicBool.
 * Rather than sharing AtomicBool across JNI, the Kotlin side signals
 * cancellation by returning true from isCancelled(). The JNI bridge
 * checks this before each partition extraction.
 */
interface ExtractionCallback {

    /**
     * Called by the extraction engine when progress updates.
     *
     * @param partitionName Name of the partition being extracted.
     * @param bytesWritten Bytes written so far for this partition.
     * @param totalBytes Total expected bytes for this partition.
     * @param operationIndex Current operation index (0-based).
     * @param totalOperations Total operations for this partition.
     */
    fun onProgress(
        partitionName: String,
        bytesWritten: Long,
        totalBytes: Long,
        operationIndex: Int,
        totalOperations: Int,
    )

    /**
     * Check if extraction should be canceled.
     *
     * @return true if extraction should stop immediately.
     */
    fun isCancelled(): Boolean
}
