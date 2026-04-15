package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Verification outcome for an extracted artifact.
 *
 * WHY no success before verification: PRD Non-Negotiable Rule #3 — no green badge,
 * no checkmark, no "Extraction complete" message until SHA-256 verification passes.
 * A passing extraction with a failing verification is NOT success.
 */
@Serializable
enum class VerificationStatus {
    /** Verification not yet attempted — extraction still in progress. */
    PENDING,

    /** SHA-256 matches the manifest target hash. Trustworthy output. */
    VERIFIED,

    /** SHA-256 does not match. Output is suspect — offer re-extraction. */
    MISMATCH,

    /** No target hash available (Forensic tier, Samsung, etc.) — cannot verify. */
    UNVERIFIABLE,
}
