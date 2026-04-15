package dev.forgeotalab.data.repository.impl

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.data.dao.PackageDao
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PackageWithPartitions
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.PackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [PackageRepository].
 *
 * WHY wrapping DAO calls in try/catch: Room can throw SQLiteException on
 * constraint violations, disk I/O errors, or corrupted databases. Wrapping
 * produces DataResult.Error with actionable diagnostics rather than crashing
 * the app — the PRD requires error states to be specific and actionable.
 */
@Singleton
class PackageRepositoryImpl @Inject constructor(
    private val packageDao: PackageDao,
    @ApplicationContext private val context: Context,
) : PackageRepository {

    override fun observeRecentPackages(limit: Int): Flow<List<PackageEntity>> {
        return packageDao.observeRecentPackages(limit)
    }

    override fun observePackageWithPartitions(packageId: String): Flow<PackageWithPartitions?> {
        return packageDao.observePackageWithPartitions(packageId)
    }

    override suspend fun importPackage(pkg: PackageEntity): DataResult<Unit> {
        return try {
            packageDao.insert(pkg)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to persist package '${pkg.displayName}': ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun updateLastOpened(packageId: String): DataResult<Unit> {
        return try {
            packageDao.updateLastOpenedAt(packageId, System.currentTimeMillis())
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to update last-opened timestamp for package $packageId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun purgeExpired(retentionDays: Int): DataResult<Int> {
        return try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val purgedCount = packageDao.purgeOlderThan(cutoff)
            DataResult.Success(purgedCount)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to purge expired packages: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getPackageById(packageId: String): DataResult<PackageEntity?> {
        return try {
            DataResult.Success(packageDao.getById(packageId))
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load package $packageId: ${e.message}",
                cause = e,
            )
        }
    }

    override fun observePackageCount(): Flow<Int> {
        return packageDao.observePackageCount()
    }

    override suspend fun deletePackage(packageId: String): DataResult<Unit> {
        return try {
            packageDao.deleteById(packageId)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to delete package $packageId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun purgeExcess(maxEntries: Int): DataResult<Int> {
        return try {
            val purgedCount = packageDao.purgeExcessEntries(maxEntries)
            DataResult.Success(purgedCount)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to purge excess history entries: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Probe a SAF URI to check if it's still accessible.
     *
     * WHY openFileDescriptor: ContentResolver.query() can return a cursor
     * even for revoked URIs in some OEM implementations. openFileDescriptor
     * is the definitive test — it fails with SecurityException if the
     * persistable permission has been revoked.
     *
     * WHY Dispatchers.IO: File descriptor probing is a blocking I/O call.
     */
    override suspend fun checkUriAccessible(sourceUri: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(sourceUri)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.close()
                pfd != null
            } catch (_: SecurityException) {
                // Persistable URI permission revoked
                false
            } catch (_: Exception) {
                // File deleted, storage disconnected, etc.
                false
            }
        }
    }
}
