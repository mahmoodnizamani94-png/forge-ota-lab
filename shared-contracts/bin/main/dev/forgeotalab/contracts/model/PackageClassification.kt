package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Classification result for an imported OTA package.
 *
 * WHY these specific variants: FR-1 requires content-based classification into
 * these categories. The Rust core sets this value during analysis — the Kotlin
 * UI renders it but never determines it independently.
 *
 * @see SupportTier for the user-facing confidence label derived from classification
 */
@Serializable
enum class PackageClassification {
    /** Google/Pixel-style or standard payload-based full OTA with verified extraction path. */
    SUPPORTED_FULL,

    /** Payload-based incremental OTA requiring validated base inputs. */
    SUPPORTED_INCREMENTAL,

    /** OEM variant matching a signed experimental adapter. */
    EXPERIMENTAL,

    /** Recognized OTA markers but no verified extraction path. Inspection only. */
    FORENSIC,

    /** Standalone partition image (boot, init_boot, vbmeta, etc.) — not an OTA package. */
    IMAGE_ONLY,

    /** Archive structure is damaged or checksums fail before analysis completes. */
    CORRUPTED,

    /** File does not match any recognized OTA or image format. */
    UNKNOWN,
}
