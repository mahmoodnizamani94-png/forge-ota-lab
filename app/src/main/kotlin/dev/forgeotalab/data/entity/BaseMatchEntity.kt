package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking base image matching for incremental OTA prerequisites.
 *
 * WHY field-level mismatch tracking: FR-4 requires the UI to show the exact
 * mismatch field per partition (fingerprint, hash, version) — not a generic
 * "wrong base image" message. Each BaseMatchEntity records which field
 * mismatched and what the expected vs actual values were.
 */
@Entity(
    tableName = "base_matches",
    foreignKeys = [
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("packageId"),
    ],
)
data class BaseMatchEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Reference to the incremental OTA package. */
    val packageId: String,

    /** Name of the partition requiring a base (e.g., "system", "vendor"). */
    val partitionName: String,

    /** Expected source build fingerprint for base validation. */
    val requiredFingerprint: String? = null,

    /** Expected SHA-256 hash of the base partition image. */
    val requiredHash: String? = null,

    /** Expected source version string for version mismatch display. */
    val requiredVersion: String? = null,

    /** Expected slot suffix (e.g., "_a", "_b") for slot mismatch display. */
    val requiredSlot: String? = null,

    /** SAF URI of the matched base image — null if no match found. */
    val matchedBaseUri: String? = null,

    /** Current match status: "MATCHED", "MISMATCHED", or "MISSING". */
    val matchStatus: String,

    /** Which validation field caused the mismatch (e.g., "FINGERPRINT", "HASH"). */
    val mismatchField: String? = null,

    /** Expected value for the mismatched field. */
    val mismatchExpected: String? = null,

    /** Actual value found in the provided base image. */
    val mismatchActual: String? = null,

    /** FK reference to BaseCacheEntity if match came from cache. */
    val baseCacheId: String? = null,

    /**
     * Advanced mode: user has explicitly opted in to raw unverified export
     * for this partition. Only effective when advanced_mode_enabled setting is on.
     * PRD FR-5: "unsafe raw export allowed per partition with explicit red warning."
     */
    val rawExportAllowed: Boolean = false,

    /** Epoch millis when validation was last performed — null if unvalidated. */
    val validatedAt: Long? = null,
)
