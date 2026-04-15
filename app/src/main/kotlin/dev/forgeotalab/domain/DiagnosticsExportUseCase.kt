package dev.forgeotalab.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.contracts.model.AdapterInfo
import dev.forgeotalab.contracts.model.DiagnosticsBundle
import dev.forgeotalab.contracts.model.EnvironmentInfo
import dev.forgeotalab.contracts.model.JobFailureInfo
import dev.forgeotalab.contracts.model.PackageFingerprint
import dev.forgeotalab.contracts.model.PartitionFailureInfo
import dev.forgeotalab.data.dao.ArtifactDao
import dev.forgeotalab.data.dao.JobDao
import dev.forgeotalab.data.dao.JobPhaseDao
import dev.forgeotalab.data.dao.PackageDao
import dev.forgeotalab.data.dao.PartitionDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Generates structured diagnostics bundles for FR-10 (Diagnostics Export).
 *
 * WHY: A support engineer receiving this bundle from a user who says "it didn't
 * work" can reconstruct exactly what happened — adapter version, detected format,
 * failure phase, failure class, device constraints — without ever touching the
 * user's OTA file.
 *
 * Bundle generation ≤ 5 seconds per PRD constraint.
 * Bundle excludes package contents and raw file paths.
 * Gracefully degrades if Room DB is partially corrupted.
 */
@Singleton
class DiagnosticsExportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageDao: PackageDao,
    private val jobDao: JobDao,
    private val jobPhaseDao: JobPhaseDao,
    private val partitionDao: PartitionDao,
    private val artifactDao: ArtifactDao,
    @Named("appVersion") private val appVersion: String,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Generate a diagnostics bundle for a specific job.
     *
     * @param jobId UUID of the failed or partial job.
     * @return File pointing to the ZIP bundle in cache, or null if generation fails entirely.
     */
    suspend fun generateForJob(jobId: String): File? {
        return try {
            val missingSections = mutableListOf<String>()

            // Collect job data — graceful if missing
            val job = try {
                jobDao.getById(jobId)
            } catch (_: Exception) {
                missingSections.add("job")
                null
            }

            val packageId = job?.packageId
            val pkg = if (packageId != null) {
                try {
                    packageDao.getById(packageId)
                } catch (_: Exception) {
                    missingSections.add("package")
                    null
                }
            } else {
                missingSections.add("package")
                null
            }

            // Collect partition-level failures
            val partitionFailures = try {
                collectPartitionFailures(jobId)
            } catch (_: Exception) {
                missingSections.add("partition_failures")
                emptyList()
            }

            // Build the bundle
            val bundle = DiagnosticsBundle(
                generatedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                packageFingerprint = buildPackageFingerprint(pkg),
                adapterInfo = buildAdapterInfo(pkg),
                supportTier = pkg?.supportTier ?: "unknown",
                jobFailure = buildJobFailureInfo(job),
                partitionFailures = partitionFailures,
                environment = collectEnvironment(),
                missingSections = missingSections,
            )

            writeBundleToZip(bundle, "diagnostics_$jobId")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generate a diagnostics bundle for a package without a job (Forensic mode).
     *
     * @param packageId UUID of the analyzed package.
     * @return File pointing to the ZIP bundle in cache.
     */
    suspend fun generateForPackage(packageId: String): File? {
        return try {
            val missingSections = mutableListOf<String>()

            val pkg = try {
                packageDao.getById(packageId)
            } catch (_: Exception) {
                missingSections.add("package")
                null
            }

            val bundle = DiagnosticsBundle(
                generatedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                packageFingerprint = buildPackageFingerprint(pkg),
                adapterInfo = buildAdapterInfo(pkg),
                supportTier = pkg?.supportTier ?: "unknown",
                environment = collectEnvironment(),
                missingSections = missingSections,
            )

            writeBundleToZip(bundle, "diagnostics_pkg_$packageId")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPackageFingerprint(
        pkg: dev.forgeotalab.data.entity.PackageEntity?,
    ): PackageFingerprint {
        return PackageFingerprint(
            fileSizeBytes = pkg?.fileSizeBytes ?: 0L,
            magicBytes = pkg?.detectedMagicBytes,
            packageFamily = pkg?.packageFamily ?: "unknown",
            classification = pkg?.classification ?: "unknown",
            isIncremental = pkg?.isIncremental ?: false,
            slotModel = pkg?.slotModel ?: "unknown",
        )
    }

    private fun buildAdapterInfo(
        pkg: dev.forgeotalab.data.entity.PackageEntity?,
    ): AdapterInfo? {
        val adapterId = pkg?.adapterId ?: return null
        val adapterVersion = pkg.adapterVersion ?: return null
        return AdapterInfo(
            adapterId = adapterId,
            adapterVersion = adapterVersion,
        )
    }

    private fun buildJobFailureInfo(
        job: dev.forgeotalab.data.entity.JobEntity?,
    ): JobFailureInfo? {
        job ?: return null
        val duration = if (job.startedAt != null && job.completedAt != null) {
            job.completedAt - job.startedAt
        } else {
            null
        }
        return JobFailureInfo(
            jobId = job.id,
            status = job.status,
            failurePhase = job.lastCheckpointPhase,
            errorSummary = job.errorSummary,
            totalPartitions = job.totalPartitions,
            completedPartitions = job.completedPartitions,
            failedPartitions = job.failedPartitions,
            durationMs = duration,
        )
    }

    private suspend fun collectPartitionFailures(jobId: String): List<PartitionFailureInfo> {
        val failedPhases = jobPhaseDao.getFailedPhasesForJob(jobId)
        return failedPhases.map { phase ->
            PartitionFailureInfo(
                partitionName = phase.partitionId ?: "unknown",
                failurePhase = phase.phase,
                errorClass = extractErrorClass(phase.errorDetails),
                // WHY no raw paths: errorDetails may contain paths from the extraction
                // engine. We strip them to only expose the error class and context.
                errorDetails = sanitizeErrorDetails(phase.errorDetails),
            )
        }
    }

    private fun extractErrorClass(errorDetails: String?): String {
        errorDetails ?: return "unknown"
        // Extract the error class from structured error context
        // Format: "ErrorType: context details" or just "ErrorType"
        return errorDetails.substringBefore(":").trim().ifEmpty { "unknown" }
    }

    private fun sanitizeErrorDetails(errorDetails: String?): String? {
        errorDetails ?: return null
        // Strip anything that looks like a file path (contains / or \)
        return errorDetails
            .replace(Regex("/[^\\s]+"), "<path>")
            .replace(Regex("\\\\[^\\s]+"), "<path>")
            .take(500) // Cap detail length
    }

    private fun collectEnvironment(): EnvironmentInfo {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return EnvironmentInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            availableStorageBytes = statFs.availableBytes,
            totalStorageBytes = statFs.totalBytes,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
        )
    }

    private fun writeBundleToZip(bundle: DiagnosticsBundle, name: String): File {
        val diagnosticsDir = File(context.cacheDir, "diagnostics")
        diagnosticsDir.mkdirs()

        val jsonFile = File(diagnosticsDir, "$name.json")
        jsonFile.writeText(json.encodeToString(bundle))

        val zipFile = File(diagnosticsDir, "$name.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("$name.json"))
            jsonFile.inputStream().use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
        }

        // Clean up the intermediate JSON file
        jsonFile.delete()

        return zipFile
    }
}
