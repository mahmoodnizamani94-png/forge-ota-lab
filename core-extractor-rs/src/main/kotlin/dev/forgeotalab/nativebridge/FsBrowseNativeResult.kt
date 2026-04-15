package dev.forgeotalab.nativebridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Typed result types for filesystem browsing JNI calls.
 *
 * WHY separate from ExtractionResult: Filesystem browsing has different
 * result shapes (directory listings, unsupported states) that don't map
 * to the extraction result model. Keeping them separate maintains clarity
 * and enables exhaustive `when` matching.
 */
sealed class FsBrowseNativeResult {

    /**
     * Filesystem image was successfully opened and root listing is available.
     *
     * The [filesystemType] string comes from the Rust core — the Kotlin UI
     * never decides filesystem type (same principle as PRD Rule #1 for support tier).
     */
    data class Browsable(
        val filesystemType: String,
        val rootListing: NativeDirectoryListing,
    ) : FsBrowseNativeResult()

    /**
     * Filesystem format is not supported for browsing.
     * PRD Failure #11: offer raw export instead of browse.
     */
    data class Unsupported(
        val format: String,
    ) : FsBrowseNativeResult()

    /**
     * Error during filesystem parsing or I/O.
     */
    data class Error(
        val code: String,
        val message: String,
        val details: String? = null,
    ) : FsBrowseNativeResult()
}

/**
 * Directory listing result — maps 1:1 to the Rust FsDirectoryListing.
 */
@Serializable
data class NativeDirectoryListing(
    val entries: List<NativeFsEntry>,
    val total_count: Long,
    val total_size: Long,
    val has_more: Boolean,
)

/**
 * A single filesystem entry — maps 1:1 to the Rust FsEntry.
 *
 * WHY snake_case fields: JSON serialization from Rust uses snake_case.
 * Using matching names avoids custom serializers and the risk of
 * name mismatch bugs.
 */
@Serializable
data class NativeFsEntry(
    val name: String,
    val path: String,
    val is_dir: Boolean,
    val size: Long,
    val permissions: String,
    val file_type: String,
    val uid: Int,
    val gid: Int,
    val modified_time: Long,
    val inode: Long,
    val links_count: Int,
)

/**
 * Result of exporting a file from inside a filesystem image.
 */
@Serializable
data class NativeFileExportResult(
    val bytes_written: Long,
    val sha256: String,
)

/**
 * Typed result for file export operations.
 */
sealed class FsFileExportNativeResult {
    data class Success(val result: NativeFileExportResult) : FsFileExportNativeResult()
    data class Error(
        val code: String,
        val message: String,
        val details: String? = null,
    ) : FsFileExportNativeResult()
}

/**
 * Typed result for directory listing operations.
 */
sealed class FsListDirectoryNativeResult {
    data class Success(val listing: NativeDirectoryListing) : FsListDirectoryNativeResult()
    data class Error(
        val code: String,
        val message: String,
        val details: String? = null,
    ) : FsListDirectoryNativeResult()
}
