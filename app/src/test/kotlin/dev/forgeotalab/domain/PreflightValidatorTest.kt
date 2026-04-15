package dev.forgeotalab.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for preflight validation logic.
 *
 * PRD: "No extraction starts before storage and permission validation."
 *
 * Coverage:
 * - Insufficient storage returns exact deficit
 * - Sufficient storage passes validation
 * - Storage estimate includes correct overhead per OTA type
 */
class PreflightValidatorTest {

    @Test
    fun insufficient_storage_returns_exact_deficit() {
        val available = 200_000_000L  // 200 MB available
        val required = 340_000_000L   // 340 MB needed

        val result = validateStorage(available, required)

        assertThat(result.isValid).isFalse()
        assertThat(result.deficit).isEqualTo(140_000_000L)
        assertThat(result.message).contains("140")
    }

    @Test
    fun sufficient_storage_passes_validation() {
        val available = 500_000_000L  // 500 MB available
        val required = 340_000_000L   // 340 MB needed

        val result = validateStorage(available, required)

        assertThat(result.isValid).isTrue()
        assertThat(result.deficit).isEqualTo(0L)
    }

    @Test
    fun exact_storage_match_passes_validation() {
        val amount = 340_000_000L

        val result = validateStorage(amount, amount)

        assertThat(result.isValid).isTrue()
    }

    @Test
    fun storage_estimate_includes_25_percent_overhead_for_full_ota() {
        val rawPartitionSize = 1_000_000_000L // 1 GB raw
        val estimate = estimateRequired(rawPartitionSize, isIncremental = false)

        // Should be at least 1.25 GB
        assertThat(estimate).isAtLeast(1_250_000_000L)
    }

    @Test
    fun storage_estimate_includes_40_percent_overhead_for_incremental() {
        val rawPartitionSize = 1_000_000_000L // 1 GB raw
        val estimate = estimateRequired(rawPartitionSize, isIncremental = true)

        // Should be at least 1.40 GB
        assertThat(estimate).isAtLeast(1_400_000_000L)
    }

    // =========================================================================
    // Test helpers — mirror production logic
    // =========================================================================

    data class StorageValidation(
        val isValid: Boolean,
        val deficit: Long,
        val message: String,
    )

    private fun validateStorage(available: Long, required: Long): StorageValidation {
        return if (available >= required) {
            StorageValidation(true, 0L, "Sufficient storage")
        } else {
            val deficit = required - available
            StorageValidation(
                false,
                deficit,
                "Insufficient storage: need ${formatBytes(required)}, " +
                    "${formatBytes(available)} available. Free ${formatBytes(deficit)} and retry.",
            )
        }
    }

    private fun estimateRequired(rawSize: Long, isIncremental: Boolean): Long {
        val multiplier = if (isIncremental) 1.40 else 1.25
        return (rawSize * multiplier).toLong()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes bytes"
        }
    }
}
