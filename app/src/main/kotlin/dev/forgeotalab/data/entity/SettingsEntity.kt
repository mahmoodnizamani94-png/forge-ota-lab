package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for key-value application settings.
 *
 * WHY key-value instead of a typed settings class: Adding a new setting
 * (e.g., "privileged_mode_enabled", "base_cache_ceiling_bytes") does not
 * require a schema migration. New features can define setting keys as
 * constants and read/write them immediately.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    /** Setting key (e.g., "theme_mode", "privileged_mode_enabled"). */
    @PrimaryKey
    val key: String,

    /** Setting value as a string — callers parse to the expected type. */
    val value: String,

    /** Epoch millis when this setting was last updated. */
    val updatedAt: Long,
)
