//! Filesystem type detection from raw image data.
//!
//! Identifies filesystem formats by reading magic bytes at known offsets.
//! v1 detects ext4 and EROFS; EROFS returns `UnsupportedFilesystem` until v1.3.
//!
//! WHY a separate module: detection logic is format-specific and will grow
//! as more filesystem parsers are added. Keeping it isolated from parsing
//! logic makes it easy to add new detectors without touching parsers.

use std::io::{Read, Seek, SeekFrom};

/// Ext4 superblock lives at byte offset 1024 in the image.
const EXT4_SUPERBLOCK_OFFSET: u64 = 1024;

/// Ext4 magic number is a little-endian u16 at offset 0x38 within the superblock.
const EXT4_MAGIC_OFFSET_IN_SUPERBLOCK: usize = 0x38;

/// Ext4 magic value: 0xEF53 (little-endian).
const EXT4_MAGIC: u16 = 0xEF53;

/// EROFS superblock lives at byte offset 1024.
const EROFS_SUPERBLOCK_OFFSET: u64 = 1024;

/// EROFS magic number is a little-endian u32 at offset 0 within the superblock.
const EROFS_MAGIC: u32 = 0xE0F5_E1E2;

/// Minimum bytes needed to detect filesystem type.
/// ext4 superblock magic is at offset 1024 + 0x38 + 2 = 1082 bytes.
/// EROFS magic is at offset 1024 + 4 = 1028 bytes.
const MIN_DETECTION_SIZE: u64 = 1082;

/// Detected filesystem type — set exclusively by the Rust core.
///
/// WHY an enum and not a string: Exhaustive matching prevents accidentally
/// forgetting to handle a new filesystem type. The Kotlin side receives
/// this as a serialized string and maps it via `when`.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize)]
pub enum FilesystemType {
    /// ext4 filesystem — supported for read-only browsing in v1.
    Ext4,
    /// EROFS filesystem — detected but not yet supported (v1.3).
    Erofs,
    /// Unknown filesystem format with captured magic bytes for diagnostics.
    Unknown(String),
}

impl std::fmt::Display for FilesystemType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FilesystemType::Ext4 => write!(f, "ext4"),
            FilesystemType::Erofs => write!(f, "EROFS"),
            FilesystemType::Unknown(magic) => write!(f, "Unknown ({magic})"),
        }
    }
}

/// Detect the filesystem type from a seekable reader.
///
/// Reads magic bytes at known offsets without consuming the entire image.
/// Returns `Unknown` if no recognized filesystem magic is found.
///
/// # Errors
/// Returns `std::io::Error` if reading or seeking fails.
pub fn detect_filesystem<R: Read + Seek>(reader: &mut R) -> std::io::Result<FilesystemType> {
    // Get file size to ensure we have enough data
    let file_size = reader.seek(SeekFrom::End(0))?;
    if file_size < MIN_DETECTION_SIZE {
        return Ok(FilesystemType::Unknown(format!(
            "image too small ({file_size} bytes)"
        )));
    }

    // --- Check ext4 ---
    // Superblock at offset 1024, magic u16 at offset 0x38 within superblock
    reader.seek(SeekFrom::Start(
        EXT4_SUPERBLOCK_OFFSET + EXT4_MAGIC_OFFSET_IN_SUPERBLOCK as u64,
    ))?;
    let mut magic_buf = [0u8; 2];
    reader.read_exact(&mut magic_buf)?;
    let ext4_magic = u16::from_le_bytes(magic_buf);

    if ext4_magic == EXT4_MAGIC {
        return Ok(FilesystemType::Ext4);
    }

    // --- Check EROFS ---
    // Superblock at offset 1024, magic u32 at offset 0 within superblock
    reader.seek(SeekFrom::Start(EROFS_SUPERBLOCK_OFFSET))?;
    let mut erofs_buf = [0u8; 4];
    reader.read_exact(&mut erofs_buf)?;
    let erofs_magic = u32::from_le_bytes(erofs_buf);

    if erofs_magic == EROFS_MAGIC {
        return Ok(FilesystemType::Erofs);
    }

    // --- Unknown ---
    // Capture first 8 bytes at offset 1024 for diagnostics
    reader.seek(SeekFrom::Start(EXT4_SUPERBLOCK_OFFSET))?;
    let mut diag_buf = [0u8; 8];
    let bytes_read = reader.read(&mut diag_buf)?;
    let hex: String = diag_buf[..bytes_read]
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect();

    Ok(FilesystemType::Unknown(hex))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    /// Build a minimal image buffer with ext4 magic at the correct offset.
    fn make_ext4_image() -> Vec<u8> {
        let mut data = vec![0u8; 4096];
        // ext4 magic 0xEF53 at superblock offset 1024 + 0x38 = 1080
        let offset = (EXT4_SUPERBLOCK_OFFSET as usize) + EXT4_MAGIC_OFFSET_IN_SUPERBLOCK;
        data[offset] = 0x53; // little-endian low byte
        data[offset + 1] = 0xEF; // little-endian high byte
        data
    }

    /// Build a minimal image buffer with EROFS magic at the correct offset.
    fn make_erofs_image() -> Vec<u8> {
        let mut data = vec![0u8; 4096];
        let offset = EROFS_SUPERBLOCK_OFFSET as usize;
        let magic_bytes = EROFS_MAGIC.to_le_bytes();
        data[offset..offset + 4].copy_from_slice(&magic_bytes);
        data
    }

    #[test]
    fn test_ext4_superblock_magic_detection_at_offset_1024() {
        let data = make_ext4_image();
        let mut cursor = Cursor::new(data);
        let fs_type = detect_filesystem(&mut cursor).unwrap();
        assert_eq!(fs_type, FilesystemType::Ext4);
    }

    #[test]
    fn test_erofs_magic_detected_returns_erofs() {
        let data = make_erofs_image();
        let mut cursor = Cursor::new(data);
        let fs_type = detect_filesystem(&mut cursor).unwrap();
        assert_eq!(fs_type, FilesystemType::Erofs);
    }

    #[test]
    fn test_non_ext4_image_returns_unknown() {
        let data = vec![0u8; 4096];
        let mut cursor = Cursor::new(data);
        let fs_type = detect_filesystem(&mut cursor).unwrap();
        assert!(matches!(fs_type, FilesystemType::Unknown(_)));
    }

    #[test]
    fn test_image_too_small_returns_unknown() {
        let data = vec![0u8; 512]; // Below MIN_DETECTION_SIZE
        let mut cursor = Cursor::new(data);
        let fs_type = detect_filesystem(&mut cursor).unwrap();
        assert!(matches!(fs_type, FilesystemType::Unknown(_)));
    }

    #[test]
    fn test_ext4_magic_takes_priority_over_erofs() {
        // An image with both magics should detect ext4 first
        let mut data = vec![0u8; 4096];
        // ext4 magic
        let ext4_offset = (EXT4_SUPERBLOCK_OFFSET as usize) + EXT4_MAGIC_OFFSET_IN_SUPERBLOCK;
        data[ext4_offset] = 0x53;
        data[ext4_offset + 1] = 0xEF;
        // EROFS magic (would be overwritten or at different offset)
        let erofs_offset = EROFS_SUPERBLOCK_OFFSET as usize;
        let magic_bytes = EROFS_MAGIC.to_le_bytes();
        data[erofs_offset..erofs_offset + 4].copy_from_slice(&magic_bytes);

        let mut cursor = Cursor::new(data);
        let fs_type = detect_filesystem(&mut cursor).unwrap();
        // ext4 is checked first
        assert_eq!(fs_type, FilesystemType::Ext4);
    }

    #[test]
    fn test_filesystem_type_display_is_human_readable() {
        assert_eq!(format!("{}", FilesystemType::Ext4), "ext4");
        assert_eq!(format!("{}", FilesystemType::Erofs), "EROFS");
        assert!(format!("{}", FilesystemType::Unknown("00".into())).contains("Unknown"));
    }
}
