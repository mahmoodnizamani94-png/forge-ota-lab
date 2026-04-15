//! Format detection and classification.
//!
//! Responsible for fingerprinting OTA packages by reading magic bytes,
//! identifying package family, and determining support tier.
//! All classification decisions originate here — the UI never decides
//! support tier independently (PRD Non-Negotiable Rule #1).
//!
//! Security: ZIP central directory scan bounded to 1000 entries (ZIP bomb guard).

use serde::Serialize;
use std::fmt;
use std::io::{Read, Seek};

/// Maximum ZIP entries scanned before aborting (ZIP bomb guard — PRD Threat Model).
const MAX_ZIP_ENTRIES: usize = 1000;

/// Minimum header size needed for magic byte detection.
const MIN_HEADER_SIZE: usize = 4;

/// Offset of TAR `ustar` magic in a TAR archive header.
const TAR_MAGIC_OFFSET: usize = 257;
/// Expected TAR magic bytes.
const TAR_MAGIC: &[u8; 5] = b"ustar";

// ---------------------------------------------------------------------------
// Magic bytes
// ---------------------------------------------------------------------------

/// Magic bytes recognized during format sniffing.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum MagicBytes {
    /// ZIP archive (PK\x03\x04)
    Zip,
    /// Chrome OS update payload (CrAU)
    CrauPayload,
    /// TAR archive (ustar at offset 257)
    Tar,
    /// Unknown magic — captured for diagnostics
    Unknown(Vec<u8>),
}

impl fmt::Display for MagicBytes {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            MagicBytes::Zip => write!(f, "ZIP (PK\\x03\\x04)"),
            MagicBytes::CrauPayload => write!(f, "CrAU payload"),
            MagicBytes::Tar => write!(f, "TAR (ustar)"),
            MagicBytes::Unknown(bytes) => write!(f, "Unknown ({:02x?})", bytes),
        }
    }
}

// ---------------------------------------------------------------------------
// Package family and support tier
// ---------------------------------------------------------------------------

/// Package family classification — the Rust core's single source of truth
/// for what kind of package this is. The Kotlin UI never decides this.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum PackageFamily {
    /// ZIP containing `payload.bin` entry — standard AOSP A/B OTA
    AospPayloadOta,
    /// Raw CrAU magic at offset 0 — standalone payload.bin file
    StandalonePayload,
    /// ZIP with .img entries but no payload.bin — legacy image zip
    LegacyImgZip,
    /// TAR magic detected — Samsung Odin format (Forensic tier in v1)
    SamsungOdin,
    /// Unrecognized format
    Unknown,
}

impl fmt::Display for PackageFamily {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            PackageFamily::AospPayloadOta => write!(f, "AOSP Payload OTA"),
            PackageFamily::StandalonePayload => write!(f, "Standalone Payload"),
            PackageFamily::LegacyImgZip => write!(f, "Legacy Image ZIP"),
            PackageFamily::SamsungOdin => write!(f, "Samsung Odin"),
            PackageFamily::Unknown => write!(f, "Unknown"),
        }
    }
}

/// Support tier — set exclusively by the Rust core.
/// PRD Non-Negotiable Rule #1: UI support tier comes only from native core output.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub enum SupportTier {
    Supported,
    Experimental,
    Forensic,
}

impl fmt::Display for SupportTier {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SupportTier::Supported => write!(f, "Supported"),
            SupportTier::Experimental => write!(f, "Experimental"),
            SupportTier::Forensic => write!(f, "Forensic"),
        }
    }
}

// ---------------------------------------------------------------------------
// Sniff result and errors
// ---------------------------------------------------------------------------

/// Result of sniffing a file header.
#[derive(Debug, Clone, Serialize)]
pub struct SniffResult {
    pub magic: MagicBytes,
    pub family: PackageFamily,
    pub tier: SupportTier,
    /// Hex string of first 4 bytes for diagnostics export.
    pub detected_magic_hex: String,
}

/// Rich error type for sniffing failures.
/// All error types implement Debug, Display, and std::error::Error per AGENTS.md.
#[derive(Debug, thiserror::Error)]
pub enum SniffError {
    #[error("I/O error reading header: {source}")]
    Io {
        #[from]
        source: std::io::Error,
    },

    #[error("Header too short: got {actual} bytes, need at least {expected}")]
    HeaderTooShort { actual: usize, expected: usize },

    #[error("ZIP scan exceeded {MAX_ZIP_ENTRIES} entry limit (ZIP bomb guard)")]
    ZipBombGuard,

    #[error("ZIP archive error: {details}")]
    ZipError { details: String },
}

// ---------------------------------------------------------------------------
// Internal ZIP scan result
// ---------------------------------------------------------------------------

/// Result of scanning a ZIP central directory.
struct ZipScanResult {
    has_payload_bin: bool,
    has_img_entries: bool,
    #[allow(dead_code)] // kept for diagnostic use
    entry_count: usize,
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/// Detect magic bytes from a buffer of at least 4 bytes.
pub fn detect_magic(header: &[u8]) -> MagicBytes {
    if header.len() < MIN_HEADER_SIZE {
        return MagicBytes::Unknown(header.to_vec());
    }

    match &header[..4] {
        [0x50, 0x4B, 0x03, 0x04] => MagicBytes::Zip,
        [0x43, 0x72, 0x41, 0x55] => MagicBytes::CrauPayload,
        _ => {
            // WHY check TAR at offset 257: TAR headers store "ustar" at byte 257,
            // not at byte 0. The first 4 bytes of a TAR are the filename, which
            // varies. We need at least 262 bytes to detect TAR reliably.
            if header.len() >= TAR_MAGIC_OFFSET + TAR_MAGIC.len()
                && &header[TAR_MAGIC_OFFSET..TAR_MAGIC_OFFSET + TAR_MAGIC.len()] == TAR_MAGIC
            {
                MagicBytes::Tar
            } else {
                MagicBytes::Unknown(header[..4].to_vec())
            }
        }
    }
}

/// Sniff a reader, reading at most 4096 bytes, and classify the package.
///
/// PRD FR-1: Format detection must complete in ≤ 500 ms for files up to 10 GB.
/// This reads only the first 4 KB, so it meets that target trivially.
pub fn sniff<R: Read>(reader: &mut R) -> Result<SniffResult, SniffError> {
    let mut header = [0u8; 4096];
    let bytes_read = read_header(reader, &mut header)?;

    if bytes_read < MIN_HEADER_SIZE {
        return Err(SniffError::HeaderTooShort {
            actual: bytes_read,
            expected: MIN_HEADER_SIZE,
        });
    }

    let buf = &header[..bytes_read];
    let magic = detect_magic(buf);
    let detected_magic_hex = format_hex(&buf[..MIN_HEADER_SIZE.min(bytes_read)]);

    let (family, tier) = classify_from_magic(&magic);

    Ok(SniffResult {
        magic,
        family,
        tier,
        detected_magic_hex,
    })
}

/// Sniff a seekable reader with ZIP central directory scanning.
///
/// This extends basic sniffing by actually reading the ZIP central directory
/// when ZIP magic is detected, looking for `payload.bin` or `.img` entries.
///
/// Security: ZIP entry scan is bounded to 1000 entries (ZIP bomb guard).
pub fn sniff_seekable<R: Read + Seek>(reader: &mut R) -> Result<SniffResult, SniffError> {
    let mut header = [0u8; 4096];
    let bytes_read = read_header(reader, &mut header)?;

    if bytes_read < MIN_HEADER_SIZE {
        return Err(SniffError::HeaderTooShort {
            actual: bytes_read,
            expected: MIN_HEADER_SIZE,
        });
    }

    let buf = &header[..bytes_read];
    let magic = detect_magic(buf);
    let detected_magic_hex = format_hex(&buf[..MIN_HEADER_SIZE.min(bytes_read)]);

    // WHY two-pass for ZIP: first pass detects ZIP magic from the header buffer.
    // Second pass reads the actual ZIP central directory to distinguish
    // AospPayloadOta (has payload.bin) from LegacyImgZip (has .img files).
    let (family, tier) = match magic {
        MagicBytes::Zip => {
            reader
                .seek(std::io::SeekFrom::Start(0))
                .map_err(|e| SniffError::Io { source: e })?;
            match scan_zip_entries(reader) {
                Ok(scan) => classify_zip_scan(&scan),
                Err(_) => (PackageFamily::Unknown, SupportTier::Forensic),
            }
        }
        _ => classify_from_magic(&magic),
    };

    Ok(SniffResult {
        magic,
        family,
        tier,
        detected_magic_hex,
    })
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

fn read_header(reader: &mut impl Read, buf: &mut [u8]) -> Result<usize, SniffError> {
    let mut total = 0;
    // WHY loop: Read::read() may return fewer bytes than requested.
    // We keep reading until the buffer is full or EOF.
    while total < buf.len() {
        match reader.read(&mut buf[total..]) {
            Ok(0) => break,
            Ok(n) => total += n,
            Err(ref e) if e.kind() == std::io::ErrorKind::Interrupted => continue,
            Err(e) => return Err(SniffError::Io { source: e }),
        }
    }
    Ok(total)
}

fn format_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect::<String>()
}

fn classify_from_magic(magic: &MagicBytes) -> (PackageFamily, SupportTier) {
    match magic {
        MagicBytes::Zip => {
            // WHY Forensic default: without scanning the ZIP central directory,
            // we can't distinguish AOSP payload OTA from legacy image ZIP.
            // Use sniff_seekable() for full classification.
            (PackageFamily::Unknown, SupportTier::Forensic)
        }
        MagicBytes::CrauPayload => (PackageFamily::StandalonePayload, SupportTier::Supported),
        MagicBytes::Tar => (PackageFamily::SamsungOdin, SupportTier::Forensic),
        MagicBytes::Unknown(_) => (PackageFamily::Unknown, SupportTier::Forensic),
    }
}

/// Scan ZIP central directory entries, bounded to MAX_ZIP_ENTRIES (ZIP bomb guard).
///
/// PRD Security: "1,000-entry limit on central directory scan. No recursive extraction."
fn scan_zip_entries<R: Read + Seek>(reader: &mut R) -> Result<ZipScanResult, SniffError> {
    let archive = zip::ZipArchive::new(reader).map_err(|e| SniffError::ZipError {
        details: e.to_string(),
    })?;

    let total_entries = archive.len();
    if total_entries > MAX_ZIP_ENTRIES {
        return Err(SniffError::ZipBombGuard);
    }

    let mut has_payload_bin = false;
    let mut has_img_entries = false;

    for i in 0..total_entries {
        let entry = archive.name_for_index(i);
        if let Some(name) = entry {
            // WHY case-insensitive: some OEM archives use mixed case for filenames.
            let lower = name.to_lowercase();
            if lower == "payload.bin" || lower.ends_with("/payload.bin") {
                has_payload_bin = true;
            }
            if lower.ends_with(".img") {
                has_img_entries = true;
            }
        }
    }

    Ok(ZipScanResult {
        has_payload_bin,
        has_img_entries,
        entry_count: total_entries,
    })
}

fn classify_zip_scan(scan: &ZipScanResult) -> (PackageFamily, SupportTier) {
    if scan.has_payload_bin {
        (PackageFamily::AospPayloadOta, SupportTier::Supported)
    } else if scan.has_img_entries {
        (PackageFamily::LegacyImgZip, SupportTier::Forensic)
    } else {
        (PackageFamily::Unknown, SupportTier::Forensic)
    }
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn test_zip_magic_detected_from_pk_header() {
        let header = [0x50, 0x4B, 0x03, 0x04, 0x00, 0x00];
        assert_eq!(detect_magic(&header), MagicBytes::Zip);
    }

    #[test]
    fn test_crau_magic_detected_from_payload_header() {
        let header = [0x43, 0x72, 0x41, 0x55, 0x00, 0x00];
        assert_eq!(detect_magic(&header), MagicBytes::CrauPayload);
    }

    #[test]
    fn test_unknown_magic_captures_bytes_for_diagnostics() {
        let header = [0xFF, 0xD8, 0xFF, 0xE0];
        assert_eq!(
            detect_magic(&header),
            MagicBytes::Unknown(vec![0xFF, 0xD8, 0xFF, 0xE0])
        );
    }

    #[test]
    fn test_short_buffer_returns_unknown() {
        let header = [0x50, 0x4B];
        assert!(matches!(detect_magic(&header), MagicBytes::Unknown(_)));
    }

    #[test]
    fn test_tar_magic_detected_at_offset_257() {
        let mut header = vec![0u8; 512];
        // TAR has "ustar" at offset 257
        header[257..262].copy_from_slice(b"ustar");
        assert_eq!(detect_magic(&header), MagicBytes::Tar);
    }

    #[test]
    fn test_tar_classified_as_samsung_forensic() {
        let mut header = vec![0u8; 512];
        header[257..262].copy_from_slice(b"ustar");
        let magic = detect_magic(&header);
        let (family, tier) = classify_from_magic(&magic);
        assert_eq!(family, PackageFamily::SamsungOdin);
        assert_eq!(tier, SupportTier::Forensic);
    }

    #[test]
    fn test_crau_classified_as_standalone_supported() {
        let magic = MagicBytes::CrauPayload;
        let (family, tier) = classify_from_magic(&magic);
        assert_eq!(family, PackageFamily::StandalonePayload);
        assert_eq!(tier, SupportTier::Supported);
    }

    #[test]
    fn test_sniff_crau_from_reader() {
        // Build a minimal CrAU header: 4 bytes magic + padding
        let mut data = vec![0u8; 4096];
        data[..4].copy_from_slice(b"CrAU");
        let mut cursor = Cursor::new(data);

        let result = sniff(&mut cursor).unwrap();
        assert_eq!(result.magic, MagicBytes::CrauPayload);
        assert_eq!(result.family, PackageFamily::StandalonePayload);
        assert_eq!(result.tier, SupportTier::Supported);
        assert_eq!(result.detected_magic_hex, "43724155");
    }

    #[test]
    fn test_sniff_header_too_short_returns_error() {
        let data = vec![0x50, 0x4B]; // only 2 bytes
        let mut cursor = Cursor::new(data);

        let result = sniff(&mut cursor);
        assert!(matches!(result, Err(SniffError::HeaderTooShort { .. })));
    }

    #[test]
    fn test_sniff_empty_reader_returns_error() {
        let data: Vec<u8> = vec![];
        let mut cursor = Cursor::new(data);

        let result = sniff(&mut cursor);
        assert!(matches!(result, Err(SniffError::HeaderTooShort { .. })));
    }

    #[test]
    fn test_zip_scan_classifies_payload_bin_as_aosp() {
        let scan = ZipScanResult {
            has_payload_bin: true,
            has_img_entries: false,
            entry_count: 5,
        };
        let (family, tier) = classify_zip_scan(&scan);
        assert_eq!(family, PackageFamily::AospPayloadOta);
        assert_eq!(tier, SupportTier::Supported);
    }

    #[test]
    fn test_zip_scan_classifies_img_only_as_legacy() {
        let scan = ZipScanResult {
            has_payload_bin: false,
            has_img_entries: true,
            entry_count: 10,
        };
        let (family, tier) = classify_zip_scan(&scan);
        assert_eq!(family, PackageFamily::LegacyImgZip);
        assert_eq!(tier, SupportTier::Forensic);
    }

    #[test]
    fn test_zip_scan_classifies_no_known_entries_as_unknown() {
        let scan = ZipScanResult {
            has_payload_bin: false,
            has_img_entries: false,
            entry_count: 3,
        };
        let (family, tier) = classify_zip_scan(&scan);
        assert_eq!(family, PackageFamily::Unknown);
        assert_eq!(tier, SupportTier::Forensic);
    }

    #[test]
    fn test_format_hex_produces_lowercase_hex_string() {
        assert_eq!(format_hex(&[0xCA, 0xFE, 0xBA, 0xBE]), "cafebabe");
    }

    #[test]
    fn test_display_implementations_are_human_readable() {
        assert_eq!(format!("{}", MagicBytes::Zip), "ZIP (PK\\x03\\x04)");
        assert_eq!(format!("{}", MagicBytes::CrauPayload), "CrAU payload");
        assert_eq!(format!("{}", MagicBytes::Tar), "TAR (ustar)");
        assert_eq!(
            format!("{}", PackageFamily::AospPayloadOta),
            "AOSP Payload OTA"
        );
        assert_eq!(format!("{}", SupportTier::Supported), "Supported");
    }

    #[test]
    fn test_sniff_result_serializes_to_json() {
        let result = SniffResult {
            magic: MagicBytes::CrauPayload,
            family: PackageFamily::StandalonePayload,
            tier: SupportTier::Supported,
            detected_magic_hex: "43724155".to_string(),
        };
        let json = serde_json::to_string(&result).unwrap();
        assert!(json.contains("StandalonePayload"));
        assert!(json.contains("Supported"));
    }
}
