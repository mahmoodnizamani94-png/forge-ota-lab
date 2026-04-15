package dev.forgeotalab.data.repository

import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PackageWithPartitions
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for OTA package persistence.
 *
 * WHY Flow for observable data, suspend for one-shot: Flow-based methods power
 * reactive UI (history list, analysis screen). Suspend methods are for
 * imperative operations (import, purge) where the caller needs a result
 * but doesn't need ongoing observation.
 *
 * ViewModels consume this interface — they never touch PackageDao directly.
 */
interface PackageRepository {

    /**
     * Observe recent packages for the history screen.
     * FR-11: reverse-chronological list, limited to [limit] entries.
     */
    fun observeRecentPackages(limit: Int = 100): Flow<List<PackageEntity>>

    /**
     * Observe a package with its partitions — N+1-free for the analysis screen.
     */
    fun observePackageWithPartitions(packageId: String): Flow<PackageWithPartitions?>

    /**
     * Persist a new package record from analysis results.
     */
    suspend fun importPackage(pkg: PackageEntity): DataResult<Unit>

    /**
     * Touch the lastOpenedAt timestamp when a user re-opens a package from history.
     */
    suspend fun updateLastOpened(packageId: String): DataResult<Unit>

    /**
     * Delete packages older than [retentionDays]. Returns count of purged records.
     * FR-11: 90-day auto-purge.
     */
    suspend fun purgeExpired(retentionDays: Int = 90): DataResult<Int>

    /**
     * One-shot lookup by ID.
     */
    suspend fun getPackageById(packageId: String): DataResult<PackageEntity?>

    /**
     * Get current package count for history limit enforcement.
     */
    fun observePackageCount(): Flow<Int>

    /**
     * Delete a package for swipe-to-delete history (FR-11).
     * Cascade deletes remove associated jobs, phases, artifacts, and partitions.
     */
    suspend fun deletePackage(packageId: String): DataResult<Unit>

    /**
     * Purge excess entries beyond the history limit (FR-11: 100 entries max).
     * Returns count of purged records.
     */
    suspend fun purgeExcess(maxEntries: Int = 100): DataResult<Int>

    /**
     * Check if a SAF URI is still accessible via ContentResolver.
     *
     * WHY a repository method: URI accessibility is a runtime check that
     * requires ContentResolver. Exposing it through the repository keeps
     * the ViewModel free of Android framework dependencies.
     */
    suspend fun checkUriAccessible(sourceUri: String): Boolean
}
