package dev.forgeotalab.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.data.repository.AnalysisOutput
import dev.forgeotalab.data.repository.AnalysisRepository
import dev.forgeotalab.data.repository.DataResult
import javax.inject.Inject

/**
 * Orchestrates the full import→analysis flow.
 *
 * WHY a use case: Separates business logic (URI permission management,
 * file metadata extraction, analysis delegation) from ViewModel concerns
 * (state management, UI events). The ViewModel calls one method and gets
 * a sealed result — it doesn't know about SAF, content resolvers, or
 * permission APIs.
 *
 * Flow:
 * 1. Take persistable URI permission (if possible)
 * 2. Query file metadata (name, size)
 * 3. Validate URI is readable
 * 4. Delegate to AnalysisRepository
 */
class ImportPackageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analysisRepository: AnalysisRepository,
) {

    /**
     * Import and analyze an OTA package from a SAF URI.
     *
     * @param uri content:// URI from SAF picker, share intent, or open-with handler.
     * @return Sealed result: Success with package ID, or specific error.
     */
    suspend fun execute(uri: Uri): ImportResult {
        // Step 1: Try to take persistable URI permission for history re-access
        // WHY try/catch: Not all URIs support persistable permissions (e.g., some
        // share intents provide one-time read access). Failure is non-fatal.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // One-time access — analysis can proceed but history re-access will fail
        }

        // Step 2: Query file metadata
        val metadata = queryFileMetadata(uri)
            ?: return ImportResult.UriInaccessible(
                message = "Source file is no longer accessible. Select the file again.",
            )

        // Step 3: Validate URI is readable
        val isReadable = try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

        if (!isReadable) {
            return ImportResult.UriInaccessible(
                message = "Cannot read the selected file. It may have been moved or deleted.",
            )
        }

        // Step 4: Delegate to analysis repository
        return when (val result = analysisRepository.analyzePackage(
            uri = uri,
            displayName = metadata.displayName,
            fileSize = metadata.sizeBytes,
        )) {
            is DataResult.Success -> ImportResult.Success(
                packageId = result.data.packageId,
                analysisOutput = result.data,
            )
            is DataResult.Error -> ImportResult.AnalysisFailed(
                message = result.message,
                cause = result.cause,
            )
        }
    }

    /**
     * Query display name and size from a content:// URI.
     *
     * WHY ContentResolver query: SAF URIs don't carry filename or size
     * information in the URI path. The OpenableColumns projection is the
     * standard way to retrieve this metadata.
     */
    private fun queryFileMetadata(uri: Uri): FileMetadata? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L

                    FileMetadata(
                        displayName = name ?: "unknown",
                        sizeBytes = size,
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Sealed result from import attempt. Enables exhaustive `when` handling
 * in the ViewModel — new variants cause compile errors at every site.
 */
sealed class ImportResult {

    /** Import and analysis succeeded — navigate to analysis screen. */
    data class Success(
        val packageId: String,
        val analysisOutput: AnalysisOutput,
    ) : ImportResult()

    /** URI is inaccessible or permission was revoked. */
    data class UriInaccessible(
        val message: String,
    ) : ImportResult()

    /** Analysis failed — Rust core error, parse error, or persistence error. */
    data class AnalysisFailed(
        val message: String,
        val cause: Throwable? = null,
    ) : ImportResult()
}

/**
 * File metadata extracted from a content:// URI.
 */
private data class FileMetadata(
    val displayName: String,
    val sizeBytes: Long,
)
