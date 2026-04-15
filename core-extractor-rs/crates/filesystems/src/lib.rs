//! Read-only filesystem parsers.
//!
//! Provides browsing of extracted partition images without mutation.
//! v1 supports ext4; EROFS detected but returns UnsupportedFilesystem (v1.3).
//!
//! **Architecture:**
//! - `detect` module identifies filesystem type from magic bytes
//! - `ext4` module provides the read-only ext4 parser
//! - This module exposes the unified public API used by forge-jni
//!
//! **PRD FR-8:** Mount read-only through Rust core. Browse directories,
//! preview metadata, export selected files/folders. Browsing never mutates
//! the source artifact.
//!
//! **PRD Failure #11:** Unsupported filesystem → offer raw export.

pub mod detect;
pub mod ext4;

use detect::FilesystemType;
use serde::Serialize;
use std::fmt;
use std::io::{Read, Seek, Write};

// ===========================================================================
// Error types
// ===========================================================================

/// Error type for filesystem operations.
///
/// Every variant carries diagnostic context per AGENTS.md.
#[derive(Debug, thiserror::Error)]
pub enum FilesystemError {
    /// The filesystem format is not supported for browsing.
    /// PRD Failure #11: offer raw export instead of browse.
    #[error("Unsupported filesystem format: {format}")]
    UnsupportedFormat { format: String },

    /// ext4-specific parser error.
    #[error("ext4 error: {0}")]
    Ext4(#[from] ext4::Ext4Error),

    /// I/O error reading the image.
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
}

impl FilesystemError {
    /// Error code string for JNI serialization.
    pub fn error_code(&self) -> &str {
        match self {
            FilesystemError::UnsupportedFormat { .. } => "UNSUPPORTED_FILESYSTEM",
            FilesystemError::Ext4(_) => "EXT4_ERROR",
            FilesystemError::Io(_) => "IO_ERROR",
        }
    }
}

// ===========================================================================
// Public result types
// ===========================================================================

/// A filesystem directory entry — the wire format for JNI.
#[derive(Debug, Clone, Serialize)]
pub struct FsEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub size: u64,
    pub permissions: String,
    pub file_type: String,
    pub uid: u16,
    pub gid: u16,
    pub modified_time: u64,
    pub inode: u32,
    pub links_count: u16,
}

/// Directory listing with pagination.
#[derive(Debug, Clone, Serialize)]
pub struct FsDirectoryListing {
    pub entries: Vec<FsEntry>,
    pub total_count: u64,
    pub total_size: u64,
    pub has_more: bool,
}

/// Result of attempting to browse a filesystem image.
#[derive(Debug, Clone, Serialize)]
pub enum FsBrowseResult {
    /// Filesystem was mounted and root listing is available.
    Browsable {
        filesystem_type: String,
        root_listing: FsDirectoryListing,
    },
    /// Filesystem format is not supported — offer raw export.
    Unsupported { format: String },
}

/// Result of exporting a file from inside a filesystem image.
#[derive(Debug, Clone, Serialize)]
pub struct FsFileExportResult {
    pub bytes_written: u64,
    pub sha256: String,
}

impl fmt::Display for FsBrowseResult {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            FsBrowseResult::Browsable {
                filesystem_type,
                root_listing,
            } => write!(
                f,
                "Browsable {} ({} entries)",
                filesystem_type, root_listing.total_count
            ),
            FsBrowseResult::Unsupported { format } => {
                write!(f, "Unsupported filesystem: {format}")
            }
        }
    }
}

// ===========================================================================
// Conversion helpers — ext4 types → public wire types
// ===========================================================================

impl From<ext4::Ext4Entry> for FsEntry {
    fn from(e: ext4::Ext4Entry) -> Self {
        FsEntry {
            name: e.name,
            path: e.path,
            is_dir: e.is_dir,
            size: e.size,
            permissions: e.permissions,
            file_type: e.file_type.to_string(),
            uid: e.uid,
            gid: e.gid,
            modified_time: e.modified_time,
            inode: e.inode,
            links_count: e.links_count,
        }
    }
}

impl From<ext4::Ext4DirectoryListing> for FsDirectoryListing {
    fn from(dl: ext4::Ext4DirectoryListing) -> Self {
        FsDirectoryListing {
            entries: dl.entries.into_iter().map(FsEntry::from).collect(),
            total_count: dl.total_count,
            total_size: dl.total_size,
            has_more: dl.has_more,
        }
    }
}

// ===========================================================================
// Public API — used by forge-jni
// ===========================================================================

/// Open a filesystem image and return the root directory listing.
///
/// Detects the filesystem type and, if supported, parses the root directory.
/// Returns `FsBrowseResult::Unsupported` for non-ext4 filesystems.
///
/// PRD FR-8: "Filesystem mount within 2 seconds for images ≤ 4 GB."
pub fn browse<R: Read + Seek>(reader: &mut R) -> Result<FsBrowseResult, FilesystemError> {
    let fs_type = detect::detect_filesystem(reader)?;

    match fs_type {
        FilesystemType::Ext4 => {
            reader.seek(std::io::SeekFrom::Start(0))?;
            let mut ext4 = ext4::Ext4Reader::open(reader)?;
            let listing = ext4.list_directory("/", 0, 500)?;
            Ok(FsBrowseResult::Browsable {
                filesystem_type: "ext4".to_string(),
                root_listing: listing.into(),
            })
        }
        FilesystemType::Erofs => Ok(FsBrowseResult::Unsupported {
            format: "EROFS — support planned for v1.3".to_string(),
        }),
        FilesystemType::Unknown(info) => Ok(FsBrowseResult::Unsupported {
            format: format!("Unknown filesystem ({info})"),
        }),
    }
}

/// List entries in a specific directory within an ext4 image.
///
/// - `path`: absolute path from root (e.g., "/system/app")
/// - `offset`: pagination offset
/// - `limit`: maximum entries to return
///
/// PRD: Lazy loading — directories load on expand, not all upfront.
pub fn list_directory<R: Read + Seek>(
    reader: &mut R,
    path: &str,
    offset: usize,
    limit: usize,
) -> Result<FsDirectoryListing, FilesystemError> {
    let fs_type = detect::detect_filesystem(reader)?;

    match fs_type {
        FilesystemType::Ext4 => {
            reader.seek(std::io::SeekFrom::Start(0))?;
            let mut ext4 = ext4::Ext4Reader::open(reader)?;
            let listing = ext4.list_directory(path, offset, limit)?;
            Ok(listing.into())
        }
        _ => Err(FilesystemError::UnsupportedFormat {
            format: fs_type.to_string(),
        }),
    }
}

/// Read a file from an ext4 image and write it to the given writer.
///
/// Returns the number of bytes written and SHA-256 hash.
/// Uses 256 KB streaming chunks — never buffers a full file.
///
/// PRD: "Source artifact is never mutated."
pub fn read_file<R: Read + Seek, W: Write>(
    reader: &mut R,
    path: &str,
    writer: &mut W,
) -> Result<FsFileExportResult, FilesystemError> {
    let fs_type = detect::detect_filesystem(reader)?;

    match fs_type {
        FilesystemType::Ext4 => {
            reader.seek(std::io::SeekFrom::Start(0))?;
            let mut ext4 = ext4::Ext4Reader::open(reader)?;
            let result = ext4.read_file(path, writer)?;
            Ok(FsFileExportResult {
                bytes_written: result.bytes_written,
                sha256: result.sha256,
            })
        }
        _ => Err(FilesystemError::UnsupportedFormat {
            format: fs_type.to_string(),
        }),
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
    fn test_browse_returns_unsupported_for_empty_image() {
        let data = vec![0u8; 4096];
        let mut cursor = Cursor::new(data);
        let result = browse(&mut cursor).unwrap();
        assert!(matches!(result, FsBrowseResult::Unsupported { .. }));
    }

    #[test]
    fn test_browse_returns_unsupported_for_erofs_image() {
        let mut data = vec![0u8; 4096];
        // EROFS magic at offset 1024
        let magic_bytes = 0xE0F5_E1E2u32.to_le_bytes();
        data[1024..1028].copy_from_slice(&magic_bytes);
        let mut cursor = Cursor::new(data);
        let result = browse(&mut cursor).unwrap();
        assert!(matches!(result, FsBrowseResult::Unsupported { .. }));
        if let FsBrowseResult::Unsupported { format } = result {
            assert!(format.contains("EROFS"));
        }
    }

    #[test]
    fn test_filesystem_error_codes_are_stable() {
        let err = FilesystemError::UnsupportedFormat {
            format: "test".into(),
        };
        assert_eq!(err.error_code(), "UNSUPPORTED_FILESYSTEM");
    }

    #[test]
    fn test_browse_result_display() {
        let result = FsBrowseResult::Unsupported {
            format: "ext4".to_string(),
        };
        assert!(format!("{result}").contains("ext4"));
    }

    #[test]
    fn test_browse_result_serializes_to_json() {
        let result = FsBrowseResult::Unsupported {
            format: "ext4".to_string(),
        };
        let json = serde_json::to_string(&result).unwrap();
        assert!(json.contains("ext4"));
    }

    #[test]
    fn test_fs_entry_from_ext4_entry() {
        let ext4_entry = ext4::Ext4Entry {
            name: "boot.img".to_string(),
            path: "/boot.img".to_string(),
            is_dir: false,
            size: 1024,
            permissions: "-rwxr-xr-x".to_string(),
            file_type: ext4::FsFileType::RegularFile,
            uid: 0,
            gid: 0,
            modified_time: 1700000000,
            inode: 42,
            links_count: 1,
        };

        let fs_entry: FsEntry = ext4_entry.into();
        assert_eq!(fs_entry.name, "boot.img");
        assert_eq!(fs_entry.file_type, "file");
        assert_eq!(fs_entry.size, 1024);
    }
}
