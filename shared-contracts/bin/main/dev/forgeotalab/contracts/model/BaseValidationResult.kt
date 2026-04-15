package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Validation field identifiers for incremental base image checks.
 *
 * WHY an exhaustive enum: FR-4 requires the UI to show the exact mismatch
 * field per partition. Using an enum instead of free-form strings ensures
 * that every new validation check produces a compile error at every
 * consumption site until it has a user-facing message. Exhaustive `when`
 * coverage prevents generic "wrong base image" errors.
 *
 * Field ordering reflects validation priority — cheapest checks first:
 * fingerprint (string compare) → partition identity → slot → version →
 * hash (SHA-256 — most expensive).
 */
@Serializable
enum class BaseValidationField {
    /** Build fingerprint (e.g., "google/raven/raven:14/AP2A.240405.003/..."). */
    FINGERPRINT,

    /** Partition name identity — base must serve the same partition. */
    PARTITION_IDENTITY,

    /** Slot expectation — base must be for the correct slot (A/B). */
    SLOT,

    /** Version string — base must match the required source version. */
    VERSION,

    /** SHA-256 hash — byte-level integrity of the base image. */
    HASH,
}

/**
 * Per-partition base validation result — the core output of prerequisite checks.
 *
 * WHY sealed: Each variant maps to a distinct visual treatment in the wizard
 * (FR-4 prerequisite wizard states). Sealed enforcement ensures every new
 * validation outcome gets UI handling — no silent failures.
 *
 * The Kotlin UI consumes these to render field-level mismatch cards per PRD:
 * "Fingerprint: expected `google/raven/...Mar 2026` — found `google/raven/...Feb 2026`"
 */
@Serializable
sealed class BaseValidationResult {

    /**
     * Base image passes all validation checks for this partition.
     * Extraction can proceed for this partition.
     */
    @Serializable
    data class Valid(
        /** SHA-256 hash of the validated base image. */
        val baseHash: String,
        /** Build fingerprint of the validated base. */
        val baseFingerprint: String,
    ) : BaseValidationResult()

    /**
     * Base image fails a specific validation check.
     * The UI shows the exact field and values per FR-4.
     *
     * @property field Which validation check failed — drives the label.
     * @property expected The value required by the incremental manifest.
     * @property actual The value found in the provided base image.
     */
    @Serializable
    data class Mismatch(
        val field: BaseValidationField,
        val expected: String,
        val actual: String,
    ) : BaseValidationResult()

    /**
     * Base image file cannot be read — SAF permission revoked, file deleted,
     * or corrupt image header.
     *
     * @property reason Human-readable explanation for the UI.
     */
    @Serializable
    data class Unreadable(
        val reason: String,
    ) : BaseValidationResult()

    /**
     * No base image has been provided yet for this partition.
     * Default state when the wizard first loads.
     */
    @Serializable
    data object Missing : BaseValidationResult()
}

/**
 * Requirements for a single partition's base image, extracted from the
 * incremental manifest during analysis.
 *
 * WHY a separate data class: These requirements are immutable once analysis
 * completes. They're persisted in BaseMatchEntity and used by the validation
 * pipeline to compare against imported base images.
 */
@Serializable
data class BaseRequirement(
    /** Partition name this requirement applies to (e.g., "system", "vendor"). */
    val partitionName: String,

    /** Required source build fingerprint — primary match criterion. */
    val requiredFingerprint: String? = null,

    /** Required SHA-256 hash of the source partition image. */
    val requiredHash: String? = null,

    /** Required source version string from manifest. */
    val requiredVersion: String? = null,

    /** Required slot suffix (e.g., "_a", "_b") — null if slot-agnostic. */
    val requiredSlot: String? = null,

    /** Source size in bytes — for display and pre-validation. */
    val sourceSize: Long? = null,
)
