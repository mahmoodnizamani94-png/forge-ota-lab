package dev.forgeotalab.domain

import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.nativebridge.FsFileExportNativeResult
import dev.forgeotalab.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain result for file export operations.
 */
sealed class ExportFileResult {
    data class Success(
        val bytesWritten: Long,
        val sha256: String,
    ) : ExportFileResult()

    data class Error(val message: String) : ExportFileResult()
}

/**
 * Exports a file from inside a filesystem image to a SAF-selected destination.
 *
 * WHY a separate use case: Export has its own security constraints (path
 * traversal guard) and workflow (SAF URI → temp path → copy). Keeping it
 * separate from browse logic maintains single-responsibility.
 *
 * PRD Security: "All output paths canonicalized and validated against
 * SAF destination before write."
 * PRD: "Source artifact is never mutated."
 */
class ExportFilesystemFileUseCase @Inject constructor(
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Export a single file from a filesystem image.
     *
     * @param artifactId UUID of the artifact containing the image.
     * @param filePath Path within the filesystem (e.g., "/system/build.prop").
     * @param outputPath Local file path to write the exported data to.
     * @return Typed result with bytes written and SHA-256 hash, or error.
     */
    suspend fun exportFile(
        artifactId: String,
        filePath: String,
        outputPath: String,
    ): ExportFileResult = withContext(Dispatchers.IO) {
        val artifact = when (val result = artifactRepository.getArtifactById(artifactId)) {
            is DataResult.Success -> result.data
            is DataResult.Error -> return@withContext ExportFileResult.Error(
                "Failed to load artifact: ${result.message}",
            )
        }

        if (artifact == null) {
            return@withContext ExportFileResult.Error("Artifact not found: $artifactId")
        }

        val imagePath = artifact.outputUri.ifBlank {
            return@withContext ExportFileResult.Error("Artifact has no output file path")
        }

        try {
            val rawJson = NativeBridge.readFile(imagePath, filePath, outputPath)
            val result = NativeBridge.parseFileExportResult(rawJson)

            when (result) {
                is FsFileExportNativeResult.Success -> {
                    ExportFileResult.Success(
                        bytesWritten = result.result.bytes_written,
                        sha256 = result.result.sha256,
                    )
                }
                is FsFileExportNativeResult.Error -> {
                    ExportFileResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            ExportFileResult.Error("Export failed: ${e.message}")
        }
    }
}
