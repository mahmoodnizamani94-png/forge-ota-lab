package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * OTA slot model classification.
 *
 * WHY: The slot model determines extraction behavior — A/B OTAs have dual
 * partition sets with slot suffixes, Virtual A/B uses copy-on-write snapshots,
 * and legacy A-only uses a single partition set. The Rust core determines this
 * from manifest metadata.
 */
@Serializable
enum class SlotModel {
    /** Standard A/B seamless update with two physical partition sets. */
    AB,

    /** Legacy single-partition model (pre-Android 7.0). */
    A_ONLY,

    /** Virtual A/B with copy-on-write snapshots (Android 11+). */
    VIRTUAL_AB,

    /** Slot model could not be determined from available metadata. */
    UNKNOWN,
}
