package dev.forgeotalab.domain

import dev.forgeotalab.contracts.model.FilesystemType
import dev.forgeotalab.contracts.model.FsEntryType
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.nativebridge.FsBrowseNativeResult
import dev.forgeotalab.nativebridge.FsListDirectoryNativeResult
import dev.forgeotalab.nativebridge.NativeBridge
import dev.forgeotalab.nativebridge.NativeDirectoryListing
import dev.forgeotalab.nativebridge.NativeFsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain model for a browsable filesystem — unpacked from native result.
 */
data class BrowsableFilesystem(
    val filesystemType: FilesystemType,
    val rootEntries: List<FsEntryDomain>,
    val totalCount: Long,
    val totalSize: Long,
    val hasMore: Boolean,
)

/**
 * Domain model for a filesystem entry — mapped from native wire type.
 *
 * WHY a domain model: The native type uses snake_case and raw strings.
 * This domain type uses Kotlin naming conventions and typed enums,
 * making it safe and idiomatic for ViewModel consumption.
 */
data class FsEntryDomain(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val permissions: String,
    val fileType: FsEntryType,
    val uid: Int,
    val gid: Int,
    val modifiedTime: Long,
    val inode: Long,
    val linksCount: Int,
)

/**
 * Domain result for browse operations — sealed for exhaustive when.
 */
sealed class BrowseFilesystemResult {
    data class Success(val filesystem: BrowsableFilesystem) : BrowseFilesystemResult()
    data class Unsupported(val format: String) : BrowseFilesystemResult()
    data class Error(val message: String, val canRawExport: Boolean = true) : BrowseFilesystemResult()
}

/**
 * Domain result for directory listing operations.
 */
sealed class ListDirectoryResult {
    data class Success(
        val entries: List<FsEntryDomain>,
        val totalCount: Long,
        val totalSize: Long,
        val hasMore: Boolean,
    ) : ListDirectoryResult()

    data class Error(val message: String) : ListDirectoryResult()
}

/**
 * Orchestrates filesystem browsing from artifact ID to directory listing.
 *
 * WHY this use case: Encapsulates the artifact → image path → Rust core
 * call chain. The ViewModel doesn't need to know how artifacts map to
 * file paths or how native results are parsed.
 *
 * PRD FR-8: "Mount read-only through Rust core."
 */
class BrowseFilesystemUseCase @Inject constructor(
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Open a filesystem image for the given artifact and return root listing.
     *
     * @param artifactId UUID string of the verified artifact to browse.
     * @return Typed domain result: Success, Unsupported, or Error.
     */
    suspend fun browseArtifact(artifactId: String): BrowseFilesystemResult =
        withContext(Dispatchers.IO) {
            val artifact = when (val result = artifactRepository.getArtifactById(artifactId)) {
                is DataResult.Success -> result.data
                is DataResult.Error -> return@withContext BrowseFilesystemResult.Error(
                    message = "Failed to load artifact: ${result.message}",
                    canRawExport = false,
                )
            }

            if (artifact == null) {
                return@withContext BrowseFilesystemResult.Error(
                    message = "Artifact not found: $artifactId",
                    canRawExport = false,
                )
            }

            val imagePath = artifact.outputUri.ifBlank {
                return@withContext BrowseFilesystemResult.Error(
                    message = "Artifact has no output file path",
                    canRawExport = false,
                )
            }

            try {
                val rawJson = NativeBridge.browseFilesystem(imagePath)
                val result = NativeBridge.parseBrowseResult(rawJson)

                when (result) {
                    is FsBrowseNativeResult.Browsable -> {
                        BrowseFilesystemResult.Success(
                            BrowsableFilesystem(
                                filesystemType = FilesystemType.fromString(result.filesystemType),
                                rootEntries = result.rootListing.entries.map { it.toDomain() },
                                totalCount = result.rootListing.total_count,
                                totalSize = result.rootListing.total_size,
                                hasMore = result.rootListing.has_more,
                            ),
                        )
                    }
                    is FsBrowseNativeResult.Unsupported -> {
                        BrowseFilesystemResult.Unsupported(result.format)
                    }
                    is FsBrowseNativeResult.Error -> {
                        BrowseFilesystemResult.Error(
                            message = result.message,
                            canRawExport = true,
                        )
                    }
                }
            } catch (e: Exception) {
                BrowseFilesystemResult.Error(
                    message = "Failed to browse filesystem: ${e.message}",
                    canRawExport = true,
                )
            }
        }

    /**
     * List entries in a specific directory within the artifact's image.
     *
     * PRD: "Lazy loading — directories load on expand, not all upfront."
     */
    suspend fun listDirectory(
        artifactId: String,
        directoryPath: String,
        offset: Long = 0,
        limit: Int = 500,
    ): ListDirectoryResult = withContext(Dispatchers.IO) {
        val artifact = when (val result = artifactRepository.getArtifactById(artifactId)) {
            is DataResult.Success -> result.data
            is DataResult.Error -> return@withContext ListDirectoryResult.Error(
                "Failed to load artifact: ${result.message}",
            )
        }

        if (artifact == null) {
            return@withContext ListDirectoryResult.Error("Artifact not found: $artifactId")
        }

        val imagePath = artifact.outputUri.ifBlank {
            return@withContext ListDirectoryResult.Error("Artifact has no output file path")
        }

        try {
            val rawJson = NativeBridge.listDirectory(imagePath, directoryPath, offset, limit)
            val result = NativeBridge.parseListDirectoryResult(rawJson)

            when (result) {
                is FsListDirectoryNativeResult.Success -> {
                    ListDirectoryResult.Success(
                        entries = result.listing.entries.map { it.toDomain() },
                        totalCount = result.listing.total_count,
                        totalSize = result.listing.total_size,
                        hasMore = result.listing.has_more,
                    )
                }
                is FsListDirectoryNativeResult.Error -> {
                    ListDirectoryResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            ListDirectoryResult.Error("Failed to list directory: ${e.message}")
        }
    }
}

/**
 * Map native wire entry to domain model.
 */
private fun NativeFsEntry.toDomain(): FsEntryDomain = FsEntryDomain(
    name = name,
    path = path,
    isDir = is_dir,
    size = size,
    permissions = permissions,
    fileType = FsEntryType.fromString(file_type),
    uid = uid,
    gid = gid,
    modifiedTime = modified_time,
    inode = inode,
    linksCount = links_count,
)
