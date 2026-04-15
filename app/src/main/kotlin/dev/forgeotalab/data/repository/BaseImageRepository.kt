package dev.forgeotalab.data.repository

import dev.forgeotalab.contracts.model.BaseValidationResult
import dev.forgeotalab.data.entity.BaseCacheEntity
import dev.forgeotalab.data.entity.BaseMatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for incremental OTA base image management.
 *
 * WHY a separate repository: Base image operations span three concerns:
 * 1. Prerequisite tracking (BaseMatchEntity — per-package)
 * 2. Physical cache management (BaseCacheEntity — cross-package)
 * 3. Validation logic (field-level comparison)
 *
 * Encapsulating all three in one repository hides the two-table design
 * from the wizard ViewModel and enables testable validation logic.
 *
 * The prerequisite gate is enforced here, not in the UI. The ViewModel
 * reads the reactive state; the use case validates via this repository.
 */
interface BaseImageRepository {

    // =========================================================================
    // Prerequisite observation — powers the wizard UI
    // =========================================================================

    /**
     * Observe all base match requirements for a package.
     * Drives the per-partition prerequisite card list in the wizard.
     */
    fun observePrerequisites(packageId: String): Flow<List<BaseMatchEntity>>

    /**
     * Observe only unresolved prerequisites.
     * Drives extraction CTA enable/disable state.
     */
    fun observeUnmatched(packageId: String): Flow<List<BaseMatchEntity>>

    /**
     * One-shot check: are all prerequisites for selected partitions met?
     * Used by PreflightValidator for domain-level gate enforcement.
     *
     * @param packageId The incremental package ID.
     * @param selectedPartitionNames Partitions selected for extraction.
     * @return true if all selected partitions have MATCHED status.
     */
    suspend fun arePrerequisitesMet(
        packageId: String,
        selectedPartitionNames: List<String>,
    ): DataResult<Boolean>

    // =========================================================================
    // Base validation — the field-level comparison pipeline
    // =========================================================================

    /**
     * Validate a user-imported base image against a specific BaseMatchEntity.
     *
     * Pipeline:
     * 1. Read the base image from SAF URI
     * 2. Compute SHA-256 hash
     * 3. Compare fingerprint → partition identity → slot → version → hash
     * 4. Update BaseMatchEntity with result and specific mismatch info
     * 5. If valid, cache the base for future use
     *
     * @param matchId UUID of the BaseMatchEntity to validate against.
     * @param baseUri SAF URI of the user-selected base image.
     * @return Validation result with field-level mismatch if applicable.
     */
    suspend fun validateBaseImage(
        matchId: String,
        baseUri: String,
    ): DataResult<BaseValidationResult>

    /**
     * Auto-match a package's prerequisites against the existing cache.
     * Called on wizard load to pre-fill validated bases from prior imports.
     *
     * @param packageId The incremental package ID.
     * @return Count of cache hits found.
     */
    suspend fun autoMatchFromCache(packageId: String): DataResult<Int>

    /**
     * Toggle raw export allowed for a specific partition (advanced mode).
     * PRD FR-5: "unsafe raw export allowed per partition when user explicitly opts in."
     */
    suspend fun setRawExportAllowed(matchId: String, allowed: Boolean): DataResult<Unit>

    // =========================================================================
    // Cache management — powers the cache management UI
    // =========================================================================

    /** Observe all cached base entries — for cache management display. */
    fun observeCacheEntries(): Flow<List<BaseCacheEntity>>

    /** Get total cache storage consumption in bytes. */
    suspend fun getCacheSizeBytes(): Long

    /** Delete a single cache entry — manual cleanup. */
    suspend fun deleteCacheEntry(id: String): DataResult<Unit>

    /** Clear entire cache. */
    suspend fun clearCache(): DataResult<Unit>
}
