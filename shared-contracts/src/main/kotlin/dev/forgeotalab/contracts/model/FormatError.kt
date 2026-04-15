package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy for analysis-phase format errors.
 *
 * WHY separate from ExtractionError: Format errors occur before any extraction
 * job is created — during import and analysis. They prevent job creation rather
 * than failing a running job. Each variant maps to a specific FR-1 bad-state
 * behavior with an actionable user message.
 */
@Serializable
sealed class FormatError {

    /** Human-readable summary for logging and diagnostics. */
    abstract val summary: String

    /**
     * Payload major version exceeds what the current adapter supports.
     * PRD FR-1: return with detected version number.
     */
    @Serializable
    data class UnsupportedVersion(
        val foundVersion: Int,
        val expectedVersion: Int,
        override val summary: String = "Unsupported payload version: found $foundVersion, " +
            "expected $expectedVersion",
    ) : FormatError()

    /**
     * Protobuf deserialization of DeltaArchiveManifest failed.
     * PRD FR-1: return with byte offset of failure.
     */
    @Serializable
    data class ManifestCorrupt(
        val byteOffset: Long,
        val details: String,
        override val summary: String = "Manifest corrupt at byte offset $byteOffset: $details",
    ) : FormatError()

    /**
     * ZIP archive is password-protected — extraction not possible.
     */
    @Serializable
    data class PasswordProtected(
        override val summary: String = "Password-protected archives are not supported",
    ) : FormatError()

    /**
     * File appears truncated — actual size smaller than manifest-declared size.
     * PRD FR-1: show actual vs expected sizes.
     */
    @Serializable
    data class TruncatedFile(
        val actualSizeBytes: Long,
        val expectedSizeBytes: Long,
        override val summary: String = "File appears truncated: $actualSizeBytes bytes " +
            "vs expected $expectedSizeBytes bytes. The download may be incomplete.",
    ) : FormatError()

    /**
     * SAF URI is inaccessible or permission was revoked before analysis.
     */
    @Serializable
    data class UriInaccessible(
        val uri: String,
        override val summary: String = "Source file is no longer accessible. Select the file again.",
    ) : FormatError()

    /**
     * Archive structure is unreadable or fundamentally corrupt.
     * PRD FR-1: surface specific structural failure.
     */
    @Serializable
    data class UnreadableArchive(
        val structureDetail: String,
        override val summary: String = "File appears corrupted — $structureDetail is invalid. " +
            "Try re-downloading the OTA package.",
    ) : FormatError()
}
