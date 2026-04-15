//! SHA-256 verification and constant-time comparison.
//!
//! PRD security requirement: SHA-256 verification uses constant-time
//! comparison, not `==`. This prevents timing side-channels that could
//! leak hash prefix information.
//!
//! Provides a streaming hasher that computes SHA-256 during extraction
//! (not after), meeting the PRD requirement that verification runs
//! concurrently with extraction I/O.

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fmt;

// ---------------------------------------------------------------------------
// Constant-time comparison
// ---------------------------------------------------------------------------

/// Constant-time comparison of two byte slices.
///
/// Returns true if and only if the slices are equal in length and content.
/// Runtime is proportional to slice length, independent of match position.
///
/// WHY constant-time: PRD Security Architecture requires constant-time hash
/// comparison for SHA-256 verification. Using `==` leaks timing information
/// that reveals how many prefix bytes matched.
pub fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }

    // XOR each byte pair and OR into accumulator.
    // Final result is zero if and only if all bytes matched.
    let mut diff: u8 = 0;
    for (x, y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

// ---------------------------------------------------------------------------
// Streaming SHA-256 hasher
// ---------------------------------------------------------------------------

/// Streaming SHA-256 hasher that processes data in chunks during extraction.
///
/// WHY streaming: the PRD (FR-7) requires computing SHA-256 during extraction,
/// not as a separate pass after writing. This hasher accumulates incremental
/// updates and finalizes to produce the digest.
pub struct StreamingHasher {
    hasher: Sha256,
    bytes_processed: u64,
}

impl StreamingHasher {
    /// Create a new streaming SHA-256 hasher.
    pub fn new() -> Self {
        Self {
            hasher: Sha256::new(),
            bytes_processed: 0,
        }
    }

    /// Feed data into the hasher. Called during extraction as each chunk
    /// is written to the output.
    pub fn update(&mut self, data: &[u8]) {
        self.hasher.update(data);
        self.bytes_processed += data.len() as u64;
    }

    /// Total bytes processed so far.
    pub fn bytes_processed(&self) -> u64 {
        self.bytes_processed
    }

    /// Finalize and return the raw 32-byte SHA-256 digest.
    pub fn finalize(self) -> [u8; 32] {
        self.hasher.finalize().into()
    }

    /// Finalize and return the hex-encoded SHA-256 digest.
    pub fn finalize_hex(self) -> String {
        let digest = self.finalize();
        hex_encode(&digest)
    }
}

impl Default for StreamingHasher {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Debug for StreamingHasher {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("StreamingHasher")
            .field("bytes_processed", &self.bytes_processed)
            .finish()
    }
}

// ---------------------------------------------------------------------------
// Verification outcome
// ---------------------------------------------------------------------------

/// Verification outcome for a single extracted artifact.
///
/// PRD Non-Negotiable Rule #3: No success state before verification completes.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum VerificationOutcome {
    /// SHA-256 matches the manifest target hash.
    Verified,
    /// SHA-256 does not match — output is suspect.
    Mismatch {
        expected: String,
        actual: String,
    },
    /// No target hash available — cannot verify (Forensic tier, etc.).
    Unverifiable {
        reason: String,
    },
}

impl fmt::Display for VerificationOutcome {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            VerificationOutcome::Verified => write!(f, "Verified"),
            VerificationOutcome::Mismatch { expected, actual } => {
                write!(f, "Mismatch: expected {expected}, got {actual}")
            }
            VerificationOutcome::Unverifiable { reason } => {
                write!(f, "Unverifiable: {reason}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Public verification API
// ---------------------------------------------------------------------------

/// Verify a computed hash against an expected hash using constant-time comparison.
///
/// Both hashes should be raw bytes (32 bytes for SHA-256).
pub fn verify_hash(expected: &[u8], actual: &[u8]) -> VerificationOutcome {
    if expected.is_empty() {
        return VerificationOutcome::Unverifiable {
            reason: "No target hash available in manifest".to_string(),
        };
    }

    if constant_time_eq(expected, actual) {
        VerificationOutcome::Verified
    } else {
        VerificationOutcome::Mismatch {
            expected: hex_encode(expected),
            actual: hex_encode(actual),
        }
    }
}

/// Convenience: verify a hex-encoded expected hash against a raw computed hash.
pub fn verify_hash_hex(expected_hex: &str, actual: &[u8]) -> VerificationOutcome {
    match hex_decode(expected_hex) {
        Some(expected_bytes) => verify_hash(&expected_bytes, actual),
        None => VerificationOutcome::Unverifiable {
            reason: format!("Invalid hex string in manifest: {expected_hex}"),
        },
    }
}

// ---------------------------------------------------------------------------
// Hex encoding/decoding
// ---------------------------------------------------------------------------

/// Encode raw bytes as lowercase hex string.
pub fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Decode a hex string to bytes. Returns None if invalid hex.
pub fn hex_decode(hex: &str) -> Option<Vec<u8>> {
    if hex.len() % 2 != 0 {
        return None;
    }
    (0..hex.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&hex[i..i + 2], 16).ok())
        .collect()
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_constant_time_eq_returns_true_for_identical_slices() {
        let a = [0x01, 0x02, 0x03];
        let b = [0x01, 0x02, 0x03];
        assert!(constant_time_eq(&a, &b));
    }

    #[test]
    fn test_constant_time_eq_returns_false_for_different_slices() {
        let a = [0x01, 0x02, 0x03];
        let b = [0x01, 0x02, 0x04];
        assert!(!constant_time_eq(&a, &b));
    }

    #[test]
    fn test_constant_time_eq_returns_false_for_different_lengths() {
        let a = [0x01, 0x02];
        let b = [0x01, 0x02, 0x03];
        assert!(!constant_time_eq(&a, &b));
    }

    #[test]
    fn test_constant_time_eq_handles_empty_slices() {
        assert!(constant_time_eq(&[], &[]));
    }

    #[test]
    fn test_streaming_hasher_matches_known_sha256() {
        // SHA-256 of empty string = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        let hasher = StreamingHasher::new();
        let hex = hasher.finalize_hex();
        assert_eq!(
            hex,
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    #[test]
    fn test_streaming_hasher_with_data() {
        // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        let mut hasher = StreamingHasher::new();
        hasher.update(b"hello");
        let hex = hasher.finalize_hex();
        assert_eq!(
            hex,
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        );
    }

    #[test]
    fn test_streaming_hasher_incremental_matches_oneshot() {
        // Incremental: "hel" + "lo" should match one-shot "hello"
        let mut h1 = StreamingHasher::new();
        h1.update(b"hel");
        h1.update(b"lo");

        let mut h2 = StreamingHasher::new();
        h2.update(b"hello");

        assert_eq!(h1.finalize(), h2.finalize());
    }

    #[test]
    fn test_streaming_hasher_tracks_bytes_processed() {
        let mut hasher = StreamingHasher::new();
        assert_eq!(hasher.bytes_processed(), 0);
        hasher.update(b"hello");
        assert_eq!(hasher.bytes_processed(), 5);
        hasher.update(b" world");
        assert_eq!(hasher.bytes_processed(), 11);
    }

    #[test]
    fn test_verify_hash_returns_verified_for_matching_hashes() {
        let expected = [0xAA; 32];
        let actual = [0xAA; 32];
        assert_eq!(verify_hash(&expected, &actual), VerificationOutcome::Verified);
    }

    #[test]
    fn test_verify_hash_returns_mismatch_with_both_values() {
        let expected = [0xAA; 32];
        let actual = [0xBB; 32];
        match verify_hash(&expected, &actual) {
            VerificationOutcome::Mismatch {
                expected: e,
                actual: a,
            } => {
                assert!(e.contains("aa"));
                assert!(a.contains("bb"));
            }
            other => panic!("Expected Mismatch, got {other:?}"),
        }
    }

    #[test]
    fn test_verify_hash_returns_unverifiable_for_empty_expected() {
        let actual = [0xAA; 32];
        assert!(matches!(
            verify_hash(&[], &actual),
            VerificationOutcome::Unverifiable { .. }
        ));
    }

    #[test]
    fn test_hex_encode_roundtrip() {
        let original = vec![0xCA, 0xFE, 0xBA, 0xBE];
        let hex = hex_encode(&original);
        assert_eq!(hex, "cafebabe");
        let decoded = hex_decode(&hex).unwrap();
        assert_eq!(decoded, original);
    }

    #[test]
    fn test_hex_decode_invalid_returns_none() {
        assert!(hex_decode("xyz").is_none());
        assert!(hex_decode("abc").is_none()); // odd length
    }

    #[test]
    fn test_verification_outcome_display() {
        assert_eq!(format!("{}", VerificationOutcome::Verified), "Verified");
        let mismatch = VerificationOutcome::Mismatch {
            expected: "aabb".to_string(),
            actual: "ccdd".to_string(),
        };
        assert!(format!("{mismatch}").contains("aabb"));
    }

    #[test]
    fn test_verification_outcome_serializes_to_json() {
        let v = VerificationOutcome::Verified;
        let json = serde_json::to_string(&v).unwrap();
        assert!(json.contains("Verified"));
    }
}
