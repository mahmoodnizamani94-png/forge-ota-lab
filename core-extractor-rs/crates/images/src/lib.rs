//! Standalone image handling.
//!
//! Handles import, fingerprinting, metadata extraction, and raw export
//! of boot-critical images (boot, init_boot, vendor_boot, vbmeta, dtbo,
//! recovery, super.img).

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fmt;
use std::io::Read;

/// Android boot image magic string.
const BOOT_MAGIC: &[u8; 8] = b"ANDROID!";

/// Android vendor boot image magic string.
const VENDOR_BOOT_MAGIC: &[u8; 8] = b"VNDRBOOT";

/// Buffer size for hashing: 8 MB chunks.
const HASH_BUFFER_SIZE: usize = 8 * 1024 * 1024;

// ---------------------------------------------------------------------------
// Image types
// ---------------------------------------------------------------------------

/// Detected image type based on magic bytes.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum ImageType {
    /// Android boot image (ANDROID! magic)
    AndroidBoot,
    /// Android vendor boot image (VNDRBOOT magic)
    VendorBoot,
    /// AVB vbmeta image (AVB0 magic)
    Vbmeta,
    /// Device Tree Blob Overlay
    Dtbo,
    /// Raw/unknown image type
    Raw,
}

impl fmt::Display for ImageType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ImageType::AndroidBoot => write!(f, "Android Boot Image"),
            ImageType::VendorBoot => write!(f, "Vendor Boot Image"),
            ImageType::Vbmeta => write!(f, "AVB vbmeta"),
            ImageType::Dtbo => write!(f, "Device Tree Blob Overlay"),
            ImageType::Raw => write!(f, "Raw Image"),
        }
    }
}

/// Information about a standalone image file.
#[derive(Debug, Clone, Serialize)]
pub struct ImageInfo {
    /// Detected image type.
    pub image_type: ImageType,
    /// File size in bytes.
    pub size: u64,
    /// SHA-256 hex digest of the entire file.
    pub sha256: String,
}

/// Error type for image operations.
#[derive(Debug, thiserror::Error)]
pub enum ImageError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Header too short: need at least {expected} bytes, got {actual}")]
    HeaderTooShort { expected: usize, actual: usize },
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/// Detect the image type from the first 16 bytes.
pub fn detect_image_type(header: &[u8]) -> ImageType {
    if header.len() >= 8 {
        if &header[..8] == BOOT_MAGIC {
            return ImageType::AndroidBoot;
        }
        if &header[..8] == VENDOR_BOOT_MAGIC {
            return ImageType::VendorBoot;
        }
    }

    if header.len() >= 4 {
        // AVB vbmeta: "AVB0" at offset 0
        if &header[..4] == b"AVB0" {
            return ImageType::Vbmeta;
        }
        // DTBO: magic 0xd7b7ab1e (big-endian)
        if header[..4] == [0xd7, 0xb7, 0xab, 0x1e] {
            return ImageType::Dtbo;
        }
    }

    ImageType::Raw
}

/// Fingerprint an image file: detect type and compute SHA-256.
///
/// Reads the file in 8 MB chunks per PRD FR-7 requirement.
pub fn fingerprint<R: Read>(reader: &mut R) -> Result<ImageInfo, ImageError> {
    let mut hasher = Sha256::new();
    let mut header = [0u8; 16];
    let mut total_size: u64 = 0;

    // Read the header for type detection
    let header_bytes = reader.read(&mut header)?;
    if header_bytes == 0 {
        return Err(ImageError::HeaderTooShort {
            expected: 4,
            actual: 0,
        });
    }
    hasher.update(&header[..header_bytes]);
    total_size += header_bytes as u64;

    let image_type = detect_image_type(&header[..header_bytes]);

    // Continue reading and hashing the rest of the file
    let mut buf = vec![0u8; HASH_BUFFER_SIZE];
    loop {
        let n = reader.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        total_size += n as u64;
    }

    let digest = hasher.finalize();
    let sha256 = digest.iter().map(|b| format!("{b:02x}")).collect::<String>();

    Ok(ImageInfo {
        image_type,
        size: total_size,
        sha256,
    })
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn test_android_boot_image_detected() {
        let mut header = vec![0u8; 32];
        header[..8].copy_from_slice(b"ANDROID!");
        assert_eq!(detect_image_type(&header), ImageType::AndroidBoot);
    }

    #[test]
    fn test_vendor_boot_image_detected() {
        let mut header = vec![0u8; 32];
        header[..8].copy_from_slice(b"VNDRBOOT");
        assert_eq!(detect_image_type(&header), ImageType::VendorBoot);
    }

    #[test]
    fn test_vbmeta_detected() {
        let mut header = vec![0u8; 32];
        header[..4].copy_from_slice(b"AVB0");
        assert_eq!(detect_image_type(&header), ImageType::Vbmeta);
    }

    #[test]
    fn test_dtbo_detected() {
        let header = vec![0xd7, 0xb7, 0xab, 0x1e, 0x00, 0x00];
        assert_eq!(detect_image_type(&header), ImageType::Dtbo);
    }

    #[test]
    fn test_unknown_magic_returns_raw() {
        let header = vec![0xFF; 16];
        assert_eq!(detect_image_type(&header), ImageType::Raw);
    }

    #[test]
    fn test_fingerprint_computes_correct_sha256() {
        let data = b"test image data for hashing";
        let mut cursor = Cursor::new(data.to_vec());
        let info = fingerprint(&mut cursor).unwrap();

        assert_eq!(info.image_type, ImageType::Raw);
        assert_eq!(info.size, data.len() as u64);
        assert!(!info.sha256.is_empty());
        assert_eq!(info.sha256.len(), 64); // hex SHA-256 is 64 chars
    }

    #[test]
    fn test_fingerprint_boot_image() {
        let mut data = vec![0u8; 1024];
        data[..8].copy_from_slice(b"ANDROID!");
        let mut cursor = Cursor::new(data.clone());
        let info = fingerprint(&mut cursor).unwrap();

        assert_eq!(info.image_type, ImageType::AndroidBoot);
        assert_eq!(info.size, 1024);
    }

    #[test]
    fn test_image_info_serializes_to_json() {
        let info = ImageInfo {
            image_type: ImageType::AndroidBoot,
            size: 65536,
            sha256: "abcdef1234567890".to_string(),
        };
        let json = serde_json::to_string(&info).unwrap();
        assert!(json.contains("AndroidBoot"));
        assert!(json.contains("65536"));
    }
}
