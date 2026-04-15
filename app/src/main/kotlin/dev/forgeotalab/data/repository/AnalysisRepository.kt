package dev.forgeotalab.data.repository

import android.net.Uri
import dev.forgeotalab.contracts.model.AnalysisResult

/**
 * Repository interface for OTA package analysis.
 *
 * WHY a separate repository: Analysis involves complex orchestration across
 * three boundaries: SAF (content:// URI) → temp cache file → JNI (Rust core)
 * → Room persistence. Encapsulating this behind an interface enables testing
 * with fake implementations and keeps the ViewModel clean.
 *
 * ViewModels call this — they never touch NativeBridge or SAF directly.
 */
interface AnalysisRepository {

    /**
     * Analyze an OTA package from a SAF URI.
     *
     * Performs the full pipeline:
     * 1. Copy content:// URI to app cache for Rust access
     * 2. Call NativeBridge.analyze() on Dispatchers.IO
     * 3. Deserialize JSON to AnalysisResult
     * 4. Persist PackageEntity + PartitionEntity records in Room
     * 5. Return result with the persisted package ID
     *
     * @param uri SAF content:// URI of the file to analyze.
     * @param displayName User-visible filename for display.
     * @param fileSize Total file size in bytes.
     * @return DataResult containing the analysis result and persisted package ID.
     */
    suspend fun analyzePackage(
        uri: Uri,
        displayName: String,
        fileSize: Long,
    ): DataResult<AnalysisOutput>

    /**
     * Re-analyze a package from its persisted URI (history re-open).
     * Uses the stored SAF URI — fails if permission was revoked.
     *
     * @param packageId UUID of the package in Room DB.
     * @return DataResult with refreshed analysis result.
     */
    suspend fun reanalyzePackage(packageId: String): DataResult<AnalysisOutput>

    /**
     * Delete the temp cache file for a package to free storage.
     */
    suspend fun cleanupCache(packageId: String)
}

/**
 * Bundled output from the analysis pipeline.
 *
 * @property packageId UUID assigned to the persisted package in Room.
 * @property analysisResult Full analysis result from the Rust core.
 */
data class AnalysisOutput(
    val packageId: String,
    val analysisResult: AnalysisResult,
)
