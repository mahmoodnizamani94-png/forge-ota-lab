package dev.forgeotalab.data.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for the Forge database.
 *
 * WHY string-based enum storage with fallback: Forward-compatibility. If the
 * database contains an enum value from a newer app version (e.g., after a
 * downgrade), the converter must not crash. The fallback strategy returns a
 * safe default rather than throwing IllegalArgumentException.
 *
 * WHY JSON for string lists: selectedPartitionIds and warnings are small,
 * bounded lists (typically 3–20 entries) that don't warrant junction tables.
 * JSON serialization via kotlinx.serialization is consistent with the JNI
 * bridge pattern established in shared-contracts.
 */
class ForgeTypeConverters {

    private val json = Json { ignoreUnknownKeys = true }

    // --- String list converters ---

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
