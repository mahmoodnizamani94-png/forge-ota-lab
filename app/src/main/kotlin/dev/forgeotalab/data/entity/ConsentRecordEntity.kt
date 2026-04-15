package dev.forgeotalab.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity recording user consent decisions.
 *
 * WHY immutable records: Consent decisions are append-only for audit trail.
 * Each record captures the decision at a specific point in time with the app
 * version — required for GDPR-style compliance where consent history must be
 * reconstructable.
 */
@Entity(tableName = "consent_records")
data class ConsentRecordEntity(
    /** UUID-based stable identifier. */
    @PrimaryKey
    val id: String,

    /** Type of consent: "TELEMETRY", "CRASH_REPORTING". */
    val consentType: String,

    /** Whether consent was granted (true) or denied (false). */
    val granted: Boolean,

    /** Epoch millis when this consent decision was recorded. */
    val recordedAt: Long,

    /** App version at the time of consent — for audit purposes. */
    val appVersion: String,
)
