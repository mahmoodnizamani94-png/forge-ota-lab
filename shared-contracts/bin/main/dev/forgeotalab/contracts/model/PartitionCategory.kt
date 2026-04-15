package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Grouping categories for partitions within an OTA package.
 *
 * WHY these groups: FR-6 specifies partition grouping for the analysis screen:
 * boot-critical, logical system, firmware/radio, metadata, misc. Users filter
 * and select by category. Launch presets map to category groups.
 */
@Serializable
enum class PartitionCategory {
    /** boot, init_boot, vendor_boot, vbmeta, dtbo, recovery — essential for boot chain. */
    BOOT_CRITICAL,

    /** system, vendor, product, system_ext, odm — logical partitions inside super. */
    LOGICAL_SYSTEM,

    /** modem, radio, firmware — baseband and firmware images. */
    FIRMWARE_RADIO,

    /** metadata, userdata, persist — storage and config partitions. */
    METADATA,

    /** Any partition not fitting the above categories. */
    MISC,
}
