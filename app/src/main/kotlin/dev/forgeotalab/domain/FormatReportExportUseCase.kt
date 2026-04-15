package dev.forgeotalab.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.contracts.model.AdapterInfo
import dev.forgeotalab.contracts.model.BuildMetadata
import dev.forgeotalab.contracts.model.FormatReport
import dev.forgeotalab.contracts.model.PartitionSummary
import dev.forgeotalab.data.dao.PackageDao
import dev.forgeotalab.data.dao.PartitionDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Generates format reports for FR-12 (Format Report Export).
 *
 * WHY separate from DiagnosticsExportUseCase: Format reports are package-scoped
 * and include partition inventories and build metadata — useful for community
 * debugging and adapter support requests. Diagnostics bundles are job-scoped
 * and include failure context.
 *
 * PRD FR-12: "Export JSON report: package classification, support tier,
 * build metadata, partition list, checksum metadata when available,
 * app version, adapter version."
 */
@Singleton
class FormatReportExportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageDao: PackageDao,
    private val partitionDao: PartitionDao,
    @Named("appVersion") private val appVersion: String,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Generate a format report for an analyzed package.
     *
     * @param packageId UUID of the analyzed package.
     * @return File pointing to the JSON report in cache, or null on failure.
     */
    suspend fun generate(packageId: String): File? {
        return try {
            val pkg = packageDao.getById(packageId) ?: return null
            val partitions = partitionDao.getByPackageId(packageId)

            val report = FormatReport(
                generatedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                classification = pkg.classification,
                supportTier = pkg.supportTier,
                packageFamily = pkg.packageFamily,
                isIncremental = pkg.isIncremental,
                slotModel = pkg.slotModel,
                buildMetadata = BuildMetadata(
                    targetFingerprint = pkg.targetFingerprint,
                    sourceFingerprint = pkg.sourceFingerprint,
                    securityPatchLevel = pkg.securityPatchLevel,
                    deviceModel = pkg.deviceModel,
                ),
                partitions = partitions.map { partition ->
                    PartitionSummary(
                        name = partition.name,
                        category = partition.category,
                        targetSize = partition.estimatedExtractedSizeBytes,
                        targetHash = partition.targetHash,
                        operationCount = partition.operationCount,
                        hasSourceOps = !partition.isExtractable,
                    )
                },
                adapterInfo = if (pkg.adapterId != null && pkg.adapterVersion != null) {
                    AdapterInfo(
                        adapterId = pkg.adapterId,
                        adapterVersion = pkg.adapterVersion,
                    )
                } else {
                    null
                },
            )

            val diagnosticsDir = File(context.cacheDir, "diagnostics")
            diagnosticsDir.mkdirs()

            val reportFile = File(diagnosticsDir, "format_report_$packageId.json")
            reportFile.writeText(json.encodeToString(report))

            reportFile
        } catch (_: Exception) {
            null
        }
    }
}
