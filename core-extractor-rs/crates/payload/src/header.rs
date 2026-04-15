//! CrAU header parsing for payload.bin files.
//!
//! Payload header layout (version 2):
//! ```text
//! Offset  Size  Field
//! 0       4     Magic bytes "CrAU"
//! 4       8     Major version (big-endian u64)
//! 12      8     Manifest size (big-endian u64)
//! 20      4     Metadata signature size (big-endian u32, v2 only)
//! ```
//!
//! Security: Rejects manifest_size > 64 MB (PRD bounded manifest).

use crate::error::PayloadError;
use std::io::Read;

/// CrAU magic bytes identifying an Android OTA payload.
pub const PAYLOAD_MAGIC: &[u8; 4] = b"CrAU";

/// Header size for version 2 payloads: 4 (magic) + 8 (version) + 8 (manifest) + 4 (sig).
pub const HEADER_SIZE_V2: usize = 24;

/// Maximum allowed manifest size: 64 MB.
/// PRD Security: "Bounded manifest parse: ≤ 64 MB allocation."
pub const MAX_MANIFEST_SIZE: u64 = 64 * 1024 * 1024;

/// Maximum supported major version. Payloads > v2 are rejected.
pub const MAX_MAJOR_VERSION: u64 = 2;

/// Parsed payload header fields.
#[derive(Debug, Clone)]
pub struct PayloadHeader {
    /// Payload major version (currently 2 for modern Android).
    pub major_version: u64,
    /// Size of the serialized DeltaArchiveManifest protobuf in bytes.
    pub manifest_size: u64,
    /// Size of the metadata signature block (0 for v1 payloads).
    pub metadata_signature_size: u32,
    /// Total header size before manifest data starts.
    pub header_size: usize,
}

impl PayloadHeader {
    /// Byte offset where the manifest protobuf data begins in the payload.
    pub fn manifest_offset(&self) -> u64 {
        self.header_size as u64
    }

    /// Byte offset where the data blobs begin (after header + manifest + metadata signature).
    pub fn data_offset(&self) -> u64 {
        self.header_size as u64 + self.manifest_size + self.metadata_signature_size as u64
    }
}

/// Parse the CrAU header from a reader.
///
/// Security invariants:
/// - Rejects non-CrAU magic (returns `InvalidMagic`)
/// - Rejects major version > 2 (returns `UnsupportedVersion`)
/// - Rejects manifest_size > 64 MB (returns `ManifestTooLarge`)
///
/// # Errors
/// Returns `PayloadError` with specific diagnostic context for each failure mode.
pub fn parse_header<R: Read + ?Sized>(reader: &mut R) -> Result<PayloadHeader, PayloadError> {
    // Read magic bytes
    let mut magic = [0u8; 4];
    reader.read_exact(&mut magic).map_err(|e| {
        if e.kind() == std::io::ErrorKind::UnexpectedEof {
            PayloadError::HeaderTruncated {
                expected: 4,
                actual: 0,
            }
        } else {
            PayloadError::Io {
                context: "reading magic bytes".to_string(),
                source: e,
            }
        }
    })?;

    if magic != *PAYLOAD_MAGIC {
        return Err(PayloadError::InvalidMagic {
            found: magic.to_vec(),
        });
    }

    // Read major version (big-endian u64)
    let major_version = read_be_u64(reader, "major version")?;
    if major_version > MAX_MAJOR_VERSION {
        return Err(PayloadError::UnsupportedVersion {
            found: major_version,
        });
    }

    // Read manifest size (big-endian u64)
    let manifest_size = read_be_u64(reader, "manifest size")?;
    if manifest_size > MAX_MANIFEST_SIZE {
        return Err(PayloadError::ManifestTooLarge {
            size: manifest_size,
        });
    }

    // Read metadata signature size (big-endian u32, v2 only)
    let metadata_signature_size = if major_version >= 2 {
        read_be_u32(reader, "metadata signature size")?
    } else {
        0
    };

    let header_size = if major_version >= 2 {
        HEADER_SIZE_V2
    } else {
        // v1: no metadata_signature_size field → 4 + 8 + 8 = 20
        20
    };

    Ok(PayloadHeader {
        major_version,
        manifest_size,
        metadata_signature_size,
        header_size,
    })
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

fn read_be_u64<R: Read + ?Sized>(reader: &mut R, field_name: &str) -> Result<u64, PayloadError> {
    let mut buf = [0u8; 8];
    reader.read_exact(&mut buf).map_err(|e| {
        if e.kind() == std::io::ErrorKind::UnexpectedEof {
            PayloadError::HeaderTruncated {
                expected: 8,
                actual: 0,
            }
        } else {
            PayloadError::Io {
                context: format!("reading {field_name}"),
                source: e,
            }
        }
    })?;
    Ok(u64::from_be_bytes(buf))
}

fn read_be_u32<R: Read + ?Sized>(reader: &mut R, field_name: &str) -> Result<u32, PayloadError> {
    let mut buf = [0u8; 4];
    reader.read_exact(&mut buf).map_err(|e| {
        if e.kind() == std::io::ErrorKind::UnexpectedEof {
            PayloadError::HeaderTruncated {
                expected: 4,
                actual: 0,
            }
        } else {
            PayloadError::Io {
                context: format!("reading {field_name}"),
                source: e,
            }
        }
    })?;
    Ok(u32::from_be_bytes(buf))
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    /// Build a valid v2 header for testing.
    fn make_v2_header(manifest_size: u64, sig_size: u32) -> Vec<u8> {
        let mut buf = Vec::new();
        buf.extend_from_slice(PAYLOAD_MAGIC);
        buf.extend_from_slice(&2u64.to_be_bytes()); // version 2
        buf.extend_from_slice(&manifest_size.to_be_bytes());
        buf.extend_from_slice(&sig_size.to_be_bytes());
        buf
    }

    #[test]
    fn test_valid_v2_header_parsed_correctly() {
        let data = make_v2_header(1024, 256);
        let mut cursor = Cursor::new(data);
        let header = parse_header(&mut cursor).unwrap();

        assert_eq!(header.major_version, 2);
        assert_eq!(header.manifest_size, 1024);
        assert_eq!(header.metadata_signature_size, 256);
        assert_eq!(header.header_size, HEADER_SIZE_V2);
    }

    #[test]
    fn test_manifest_offset_calculation() {
        let data = make_v2_header(4096, 512);
        let mut cursor = Cursor::new(data);
        let header = parse_header(&mut cursor).unwrap();

        assert_eq!(header.manifest_offset(), 24); // header size
        assert_eq!(header.data_offset(), 24 + 4096 + 512);
    }

    #[test]
    fn test_invalid_magic_rejected() {
        let mut data = make_v2_header(1024, 0);
        data[0..4].copy_from_slice(b"NOPE");
        let mut cursor = Cursor::new(data);

        let result = parse_header(&mut cursor);
        assert!(matches!(result, Err(PayloadError::InvalidMagic { .. })));
    }

    #[test]
    fn test_unsupported_version_3_rejected() {
        let mut buf = Vec::new();
        buf.extend_from_slice(PAYLOAD_MAGIC);
        buf.extend_from_slice(&3u64.to_be_bytes()); // version 3
        buf.extend_from_slice(&1024u64.to_be_bytes());
        let mut cursor = Cursor::new(buf);

        let result = parse_header(&mut cursor);
        match result {
            Err(PayloadError::UnsupportedVersion { found }) => assert_eq!(found, 3),
            other => panic!("Expected UnsupportedVersion, got {other:?}"),
        }
    }

    #[test]
    fn test_manifest_size_over_64mb_rejected() {
        let over_limit = MAX_MANIFEST_SIZE + 1;
        let data = make_v2_header(over_limit, 0);
        let mut cursor = Cursor::new(data);

        let result = parse_header(&mut cursor);
        match result {
            Err(PayloadError::ManifestTooLarge { size }) => assert_eq!(size, over_limit),
            other => panic!("Expected ManifestTooLarge, got {other:?}"),
        }
    }

    #[test]
    fn test_manifest_size_exactly_64mb_accepted() {
        let data = make_v2_header(MAX_MANIFEST_SIZE, 0);
        let mut cursor = Cursor::new(data);

        let header = parse_header(&mut cursor).unwrap();
        assert_eq!(header.manifest_size, MAX_MANIFEST_SIZE);
    }

    #[test]
    fn test_truncated_header_returns_error() {
        let data = vec![0x43, 0x72]; // only 2 bytes
        let mut cursor = Cursor::new(data);

        let result = parse_header(&mut cursor);
        assert!(matches!(
            result,
            Err(PayloadError::HeaderTruncated { .. }) | Err(PayloadError::Io { .. })
        ));
    }

    #[test]
    fn test_empty_reader_returns_error() {
        let data: Vec<u8> = vec![];
        let mut cursor = Cursor::new(data);

        let result = parse_header(&mut cursor);
        assert!(result.is_err());
    }
}
