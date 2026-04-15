package dev.forgeotalab.domain

import dev.forgeotalab.data.repository.BaseImageRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.SettingsRepository
import java.util.Locale
import javax.inject.Inject

/**
 * Manages the LRU base image cache lifecycle.
 *
 * WHY a dedicated manager: Cache operations require coordination between
 * settings (ceiling), filesystem (disk space), and Room (metadata). The
 * SettingsRepository owns the ceiling configuration; this class owns the
 * eviction and auto-match logic that operates against that ceiling.
 *
 * PRD: "Cache previously verified base partitions for future incremental
 * matching (LRU eviction, configurable storage ceiling)."
 */
class BaseCacheManager @Inject constructor(
    private val baseImageRepository: BaseImageRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Auto-match a package's prerequisites against cached bases.
     * Called on wizard load to pre-fill validated bases from prior imports.
     *
     * @param packageId The incremental package ID.
     * @return Count of cache hits, or 0 on error.
     */
    suspend fun autoMatch(packageId: String): Int {
        return when (val result = baseImageRepository.autoMatchFromCache(packageId)) {
            is DataResult.Success -> result.data
            is DataResult.Error -> 0
        }
    }

    /**
     * Get the configured cache ceiling in bytes.
     * Default: 2 GB.
     */
    suspend fun getCeilingBytes(): Long {
        val result = settingsRepository.getSetting(
            SettingsRepository.KEY_BASE_CACHE_CEILING_BYTES,
        )
        return when (result) {
            is DataResult.Success -> result.data?.toLongOrNull() ?: DEFAULT_CEILING
            is DataResult.Error -> DEFAULT_CEILING
        }
    }

    /**
     * Update the cache ceiling.
     *
     * @param bytes New ceiling in bytes. Must be ≥ MIN_CEILING.
     */
    suspend fun setCeilingBytes(bytes: Long): DataResult<Unit> {
        val clamped = bytes.coerceAtLeast(MIN_CEILING)
        return settingsRepository.setSetting(
            SettingsRepository.KEY_BASE_CACHE_CEILING_BYTES,
            clamped.toString(),
        )
    }

    /**
     * Get current cache size vs ceiling for display.
     *
     * @return Pair of (currentBytes, ceilingBytes).
     */
    suspend fun getCacheUsage(): Pair<Long, Long> {
        val current = baseImageRepository.getCacheSizeBytes()
        val ceiling = getCeilingBytes()
        return current to ceiling
    }

    /**
     * Format byte count for display.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1_048_576 -> "${bytes / 1024} KB"
            bytes < 1_073_741_824 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            else -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
        }
    }

    companion object {
        /** Default cache ceiling: 2 GB */
        const val DEFAULT_CEILING = 2L * 1024 * 1024 * 1024

        /** Minimum ceiling: 100 MB — below this, caching is pointless */
        const val MIN_CEILING = 100L * 1024 * 1024

        /** Maximum ceiling: 10 GB — reasonable upper bound for mobile */
        const val MAX_CEILING = 10L * 1024 * 1024 * 1024
    }
}
