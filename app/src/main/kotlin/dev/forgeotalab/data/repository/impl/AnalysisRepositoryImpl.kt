package dev.forgeotalab.data.repository.impl

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.contracts.model.AnalysisResult
import dev.forgeotalab.contracts.model.PartitionInfo
import dev.forgeotalab.data.dao.PackageDao
import dev.forgeotalab.data.dao.PartitionDao
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PartitionEntity
import dev.forgeotalab.data.repository.AnalysisOutput
import dev.forgeotalab.data.repository.AnalysisRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room + JNI backed implementation of [AnalysisRepository].
 *
 * Pipeline: SAF URI → temp cache copy → Rust analyze() → JSON parse → Room persist.
 *
 * WHY copy to cache: The Rust core takes a file path, but SAF provides
 * content:// URIs. For analysis we only need the first ~64 MB (header +
 * manifest), but for simplicity and correctness we copy the relevant
 * portion. Full file copy is acceptable for v1 where analysis and
 * extraction happen on the same cached file.
 *
 * WHY Dispatchers.IO: File I/O (cache copy) and JNI calls (Rust analysis)
 * are blocking operations. Running on Dispatchers.IO prevents Main thread
 * blocking and potential ANR.
 */
@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageDao: PackageDao,
    private val partitionDao: PartitionDao,
) : AnalysisRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun analyzePackage(
        uri: Uri,
        displayName: String,
        fileSize: Long,
    ): DataResult<AnalysisOutput> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Copy content:// URI to app cache for Rust access
            val cacheFile = copyUriToCache(uri, displayName)
                ?: return@withContext DataResult.Error(
                    message = "Source file is no longer accessible. Select the file again.",
                )

            // Step 2: Call Rust core analysis
            val rawJson = NativeBridge.analyze(cacheFile.absolutePath)

            // Step 3: Parse the JNI result envelope
            val result = parseAnalysisJson(rawJson)
                ?: return@withContext DataResult.Error(
                    message = "Analysis failed: unable to parse native core response.",
                )

            // Step 4: Persist to Room
            val packageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val packageEntity = PackageEntity(
                id = packageId,
                sourceUri = uri.toString(),
                displayName = displayName,
                fileSizeBytes = fileSize,
                packageFamily = result.packageFamily,
                classification = mapClassification(result),
                supportTier = result.supportTier,
                isIncremental = result.isIncremental,
                slotModel = "UNKNOWN",
                securityPatchLevel = result.securityPatchLevel,
                manifestSizeBytes = result.manifestSize,
                payloadSizeBytes = result.totalPayloadSize,
                importedAt = now,
                lastOpenedAt = now,
                analysisComplete = true,
                detectedMagicBytes = "43724155", // CrAU in hex
            )

            packageDao.insert(packageEntity)

            val partitionEntities = result.partitions.mapIndexed { index, partition ->
                mapPartitionToEntity(partition, packageId)
            }
            partitionDao.insertAll(partitionEntities)

            DataResult.Success(
                AnalysisOutput(
                    packageId = packageId,
                    analysisResult = result,
                ),
            )
        } catch (e: Exception) {
            DataResult.Error(
                message = "Analysis failed: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun reanalyzePackage(packageId: String): DataResult<AnalysisOutput> {
        return DataResult.Error(
            message = "Re-analysis not yet implemented. Select the file again.",
        )
    }

    override suspend fun cleanupCache(packageId: String) {
        // Cache cleanup will be implemented when extraction caching is formalized
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Copy a content:// URI to the app's private cache directory.
     *
     * WHY private cache: App-private cache is accessible by file path (needed
     * for Rust JNI), auto-cleaned by the system under storage pressure, and
     * does not require any additional permissions.
     */
    private fun copyUriToCache(uri: Uri, displayName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "analysis")
            cacheDir.mkdirs()

            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val cacheFile = File(cacheDir, "${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            } ?: return null

            cacheFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse the JNI JSON envelope and extract the AnalysisResult.
     *
     * JNI envelope format:
     * - Success: {"ok": <AnalysisResult>}
     * - Error:   {"error": {"code": "...", "message": "...", "details": "..."}}
     */
    private fun parseAnalysisJson(rawJson: String): AnalysisResult? {
        return try {
            val envelope = json.parseToJsonElement(rawJson).jsonObject

            if (envelope.containsKey("ok")) {
                val okElement = envelope["ok"] ?: return null
                json.decodeFromJsonElement(AnalysisResult.serializer(), okElement)
            } else {
                // Error envelope — extract error details for logging
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Map AnalysisResult to a PackageClassification string for Room storage.
     */
    private fun mapClassification(result: AnalysisResult): String {
        return when {
            result.supportTier == "Supported" && !result.isIncremental -> "SUPPORTED_FULL"
            result.supportTier == "Supported" && result.isIncremental -> "SUPPORTED_INCREMENTAL"
            result.supportTier == "Experimental" -> "EXPERIMENTAL"
            result.supportTier == "Forensic" -> "FORENSIC"
            else -> "UNKNOWN"
        }
    }

    /**
     * Map a Rust PartitionInfo to a Room PartitionEntity.
     */
    private fun mapPartitionToEntity(
        partition: PartitionInfo,
        packageId: String,
    ): PartitionEntity {
        return PartitionEntity(
            id = UUID.randomUUID().toString(),
            packageId = packageId,
            name = partition.name,
            category = partition.categoryEnum.name,
            sizeBytes = partition.targetSize,
            estimatedExtractedSizeBytes = partition.targetSize,
            operationCount = partition.operationCount,
            compressAlgorithm = partition.compressionAlgorithm,
            isExtractable = partition.isExtractable,
            notExtractableReason = partition.notExtractableReason,
            sourceHash = partition.sourceHash,
            targetHash = partition.targetHash,
        )
    }
}
