//! Rich error types for payload parsing and extraction.
//!
//! Every error variant carries the diagnostic context needed to produce
//! actionable error messages on the Kotlin side without guessing.
//! All types implement Debug, Display, and std::error::Error.


// ---------------------------------------------------------------------------
// Payload parsing errors (analysis phase)
// ---------------------------------------------------------------------------

/// Errors during payload header parsing and manifest deserialization.
///
/// These occur before any extraction job is created — they prevent job
/// creation rather than failing a running job.
#[derive(Debug, thiserror::Error)]
pub enum PayloadError {
    /// Magic bytes are not "CrAU".
    #[error("Invalid magic bytes: expected CrAU, found {found:?}")]
    InvalidMagic { found: Vec<u8> },

    /// Payload major version exceeds what we support (currently v2).
    /// PRD FR-1: return with detected version number.
    #[error("Unsupported payload version: found {found}, expected ≤ 2")]
    UnsupportedVersion { found: u64 },

    /// Manifest size exceeds the 64 MB security bound.
    /// PRD Security: "Bounded manifest parse: ≤ 64 MB allocation."
    #[error("Manifest size exceeds 64 MB limit: {size} bytes")]
    ManifestTooLarge { size: u64 },

    /// Protobuf deserialization failed.
    /// PRD FR-1: return with byte offset of failure.
    #[error("Manifest corrupt: {details}")]
    ManifestCorrupt { details: String },

    /// Parse exceeded the 10-second timeout.
    /// PRD Security: "10-second timeout."
    #[error("Manifest parsing timed out after {elapsed_ms} ms (limit: {limit_ms} ms)")]
    ParseTimeout { elapsed_ms: u64, limit_ms: u64 },

    /// Header is truncated — not enough bytes to read required fields.
    #[error("Header truncated: expected at least {expected} bytes, got {actual}")]
    HeaderTruncated { expected: usize, actual: usize },

    /// I/O error during parsing.
    #[error("I/O error during {context}: {source}")]
    Io {
        context: String,
        #[source]
        source: std::io::Error,
    },

    /// Protobuf decode error from prost.
    #[error("Protobuf decode error: {0}")]
    ProtobufDecode(#[from] prost::DecodeError),
}

// ---------------------------------------------------------------------------
// Extraction errors (extraction phase)
// ---------------------------------------------------------------------------

/// Errors during partition extraction.
///
/// Each variant maps to a specific PRD failure class with actionable context.
/// The extraction engine uses these to report per-partition failures while
/// continuing with remaining partitions (partial success).
#[derive(Debug, thiserror::Error)]
pub enum ExtractionError {
    /// Decompression bomb detected — output exceeds declared_size × 1.01.
    /// PRD Security: "Abort if bytes_written > declared_size × 1.01."
    #[error(
        "Decompression bomb: wrote {actual} bytes, declared size {declared} \
         (limit: {limit}, triggered at 1.01× ratio)"
    )]
    DecompressionBomb { actual: u64, declared: u64, limit: u64 },

    /// Streaming decompression failed.
    /// PRD FR-3: mark partition as failed, continue with remaining.
    #[error("Decompression failed ({algorithm}): {details}")]
    DecompressFailed { algorithm: String, details: String },

    /// Integer overflow in extent calculation.
    /// PRD Security: "All extent calculations use checked_mul/checked_add."
    #[error("Integer overflow in extent calculation: {context}")]
    IntegerOverflow { context: String },

    /// User cancelled extraction.
    /// PRD Failure #9: preserve verified outputs, clean temp files.
    #[error("Extraction cancelled by user")]
    Cancelled,

    /// Operation type not supported for extraction (e.g., BSDIFF in full OTA mode).
    #[error("Unsupported operation type {op_type} for partition '{partition}'")]
    UnsupportedOperation { op_type: i32, partition: String },

    /// I/O error during extraction.
    /// PRD FR-3: mark partition as failed with I/O error details.
    #[error("I/O error during extraction of '{partition}': {context} — {source}")]
    Io {
        partition: String,
        context: String,
        #[source]
        source: std::io::Error,
    },

    /// SHA-256 verification failed after extraction.
    /// PRD FR-7: partition marked as VERIFICATION_FAILED.
    #[error(
        "Verification failed for '{partition}': expected {expected}, got {actual}"
    )]
    VerificationFailed {
        partition: String,
        expected: String,
        actual: String,
    },

    /// Extracted size exceeds declared partition size.
    /// PRD Failure #15: abort partition extraction.
    #[error(
        "Size exceeds limit for '{partition}': wrote {actual} bytes, \
         declared {declared} bytes"
    )]
    SizeExceedsLimit {
        partition: String,
        actual: u64,
        declared: u64,
    },

    /// Path traversal attempt detected.
    /// PRD Security: "Canonicalize output paths. Reject if resolved path escapes."
    #[error("Path traversal detected: {path} escapes destination {destination}")]
    PathTraversal { path: String, destination: String },

    /// Corrupted operation data or blob read failure.
    /// PRD FR-3: retry read once, on second failure mark partition as failed.
    #[error(
        "Corrupted operation #{op_index} for '{partition}' at offset {data_offset}: \
         expected {expected} bytes, got {actual}"
    )]
    CorruptedOperation {
        partition: String,
        op_index: usize,
        data_offset: u64,
        expected: u64,
        actual: u64,
    },
}

impl ExtractionError {
    /// Returns an error code string suitable for JNI serialization.
    pub fn error_code(&self) -> &'static str {
        match self {
            ExtractionError::DecompressionBomb { .. } => "DECOMPRESSION_BOMB",
            ExtractionError::DecompressFailed { .. } => "DECOMPRESS_FAILED",
            ExtractionError::IntegerOverflow { .. } => "INTEGER_OVERFLOW",
            ExtractionError::Cancelled => "CANCELLED",
            ExtractionError::UnsupportedOperation { .. } => "UNSUPPORTED_OPERATION",
            ExtractionError::Io { .. } => "IO_ERROR",
            ExtractionError::VerificationFailed { .. } => "VERIFICATION_FAILED",
            ExtractionError::SizeExceedsLimit { .. } => "SIZE_EXCEEDS_LIMIT",
            ExtractionError::PathTraversal { .. } => "PATH_TRAVERSAL",
            ExtractionError::CorruptedOperation { .. } => "CORRUPTED_OPERATION",
        }
    }
}



#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_payload_error_display_includes_context() {
        let err = PayloadError::UnsupportedVersion { found: 3 };
        let msg = format!("{err}");
        assert!(msg.contains("3"));
        assert!(msg.contains("Unsupported"));
    }

    #[test]
    fn test_extraction_error_display_includes_partition_name() {
        let err = ExtractionError::DecompressFailed {
            algorithm: "xz".to_string(),
            details: "stream corrupted".to_string(),
        };
        let msg = format!("{err}");
        assert!(msg.contains("xz"));
        assert!(msg.contains("stream corrupted"));
    }

    #[test]
    fn test_extraction_error_codes_are_uppercase_identifiers() {
        let err = ExtractionError::DecompressionBomb {
            actual: 100,
            declared: 50,
            limit: 51,
        };
        assert_eq!(err.error_code(), "DECOMPRESSION_BOMB");
    }

    #[test]
    fn test_manifest_too_large_error_shows_size() {
        let err = PayloadError::ManifestTooLarge {
            size: 100_000_000,
        };
        assert!(format!("{err}").contains("100000000"));
    }

    #[test]
    fn test_manifest_corrupt_error_shows_details() {
        let err = PayloadError::ManifestCorrupt {
            details: "unexpected EOF at field 13".to_string(),
        };
        assert!(format!("{err}").contains("unexpected EOF"));
    }
}
