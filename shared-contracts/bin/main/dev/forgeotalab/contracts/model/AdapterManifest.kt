package dev.forgeotalab.contracts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Signed adapter manifest — the remote control surface for adapter expansion.
 *
 * WHY: The PRD's "Signed Manifest Contract" defines a JSON envelope with:
 * adapter versions, compatibility notes, minimum app version, revocation list,
 * support-tier flags, and feature flags. Ed25519 signature is validated BEFORE
 * any field in this structure is accessed.
 *
 * Manifest JSON structure (over the wire):
 * {
 *   "signature": "<base64 Ed25519 signature of body>",
 *   "body": { ...this structure... }
 * }
 */
@Serializable
data class AdapterManifest(
    /** Manifest schema version for forward compatibility. */
    @SerialName("manifest_version")
    val manifestVersion: Int = 1,

    /** Epoch millis when this manifest was published. */
    @SerialName("published_at")
    val publishedAt: Long,

    /** Minimum app version required to use this manifest. */
    @SerialName("minimum_app_version")
    val minimumAppVersion: String,

    /** Full set of available adapters. */
    val adapters: List<ManifestAdapter>,

    /** Adapter IDs to revoke — removed from active registry immediately. */
    val revocations: List<String> = emptyList(),

    /** Feature flags controlled by the manifest service. */
    @SerialName("feature_flags")
    val featureFlags: ManifestFeatureFlags = ManifestFeatureFlags(),
)

/**
 * Adapter entry within the signed manifest.
 */
@Serializable
data class ManifestAdapter(
    /** Adapter ID (e.g., "google_pixel_full_v1"). */
    val id: String,

    /** OTA family this adapter handles. */
    val family: String,

    /** Semantic version string. */
    val version: String,

    /** Support tier for this adapter. */
    @SerialName("support_tier")
    val supportTier: String,

    /** Minimum app version to use this adapter. */
    @SerialName("minimum_app_version")
    val minimumAppVersion: String? = null,

    /** Human-readable compatibility notes. */
    @SerialName("compatibility_notes")
    val compatibilityNotes: String? = null,
)

/**
 * Feature flags from the signed manifest.
 *
 * WHY: Rollout controls per PRD — per-family flags, kill switches,
 * concurrent job limits. These are enforced locally but controlled remotely.
 */
@Serializable
data class ManifestFeatureFlags(
    /** Maximum concurrent extraction jobs. V1 = 1. */
    @SerialName("concurrent_job_limit")
    val concurrentJobLimit: Int = 1,

    /** Whether privileged mode toggle is visible in settings. */
    @SerialName("privileged_mode_enabled")
    val privilegedModeEnabled: Boolean = false,

    /** Whether incremental reconstruction is enabled. */
    @SerialName("incremental_reconstruction_enabled")
    val incrementalReconstructionEnabled: Boolean = true,
)

/**
 * Wire format for the signed manifest envelope.
 * The signature covers the raw bytes of the "body" JSON string.
 */
@Serializable
data class SignedManifestEnvelope(
    /** Base64-encoded Ed25519 signature of the body field's raw JSON bytes. */
    val signature: String,

    /** The manifest body as a raw JSON string — verified before parsing. */
    val body: String,
)
