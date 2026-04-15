//! Synthetic corpus generator for Forge OTA Lab integration tests.
//!
//! Produces minimal, deterministic test fixtures that exercise every
//! code path in the sniff → analyze → extract → verify pipeline.
//!
//! Fixtures are pure binary blobs built from Rust types — no external
//! OTA files needed. Total output < 500 KB.
//!
//! Usage: `cargo run -p corpus-gen`
//! Output: `../../test-corpus/` (relative to this crate)

use forge_payload::proto::{
    DeltaArchiveManifest, Extent, InstallOperation, OpType, PartitionInfo, PartitionUpdate,
};
use forge_verification::hex_encode;
use prost::Message;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::Write;
use std::path::Path;
use zip::write::SimpleFileOptions;

fn main() {
    let output_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-corpus");
    fs::create_dir_all(&output_dir).expect("Failed to create test-corpus directory");

    println!("Generating synthetic corpus in: {}", output_dir.display());

    generate_valid_full_ota(&output_dir);
    generate_valid_incremental(&output_dir);
    generate_ota_zip(&output_dir);
    generate_legacy_img_zip(&output_dir);
    generate_corrupted_manifest(&output_dir);
    generate_truncated_header(&output_dir);

    println!("Corpus generation complete.");
    print_inventory(&output_dir);
}

// ---------------------------------------------------------------------------
// Fixture 1: Valid full OTA payload.bin
// ---------------------------------------------------------------------------

/// Build a valid CrAU v2 payload.bin with 2 REPLACE partitions.
///
/// The data blobs contain deterministic content so SHA-256 hashes
/// embedded in the manifest match the actual extraction output.
fn generate_valid_full_ota(dir: &Path) {
    println!("  [1/6] valid_full_ota.bin");

    // Partition data: deterministic patterns
    let boot_data = generate_partition_data(b"FORGE_BOOT_IMG_", 65536); // 64 KB
    let vbmeta_data = generate_partition_data(b"FORGE_VBMETA__", 4096); // 4 KB

    let boot_hash = sha256_bytes(&boot_data);
    let vbmeta_hash = sha256_bytes(&vbmeta_data);

    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(0), // Full OTA
        partitions: vec![
            PartitionUpdate {
                partition_name: "boot".to_string(),
                new_partition_info: Some(PartitionInfo {
                    size: Some(boot_data.len() as u64),
                    hash: Some(boot_hash.clone()),
                }),
                operations: vec![InstallOperation {
                    r#type: OpType::Replace as i32,
                    data_offset: Some(0),
                    data_length: Some(boot_data.len() as u64),
                    dst_extents: vec![Extent {
                        start_block: Some(0),
                        num_blocks: Some(boot_data.len() as u64 / 4096),
                    }],
                    ..Default::default()
                }],
                ..Default::default()
            },
            PartitionUpdate {
                partition_name: "vbmeta".to_string(),
                new_partition_info: Some(PartitionInfo {
                    size: Some(vbmeta_data.len() as u64),
                    hash: Some(vbmeta_hash.clone()),
                }),
                operations: vec![InstallOperation {
                    r#type: OpType::Replace as i32,
                    data_offset: Some(boot_data.len() as u64),
                    data_length: Some(vbmeta_data.len() as u64),
                    dst_extents: vec![Extent {
                        start_block: Some(0),
                        num_blocks: Some(vbmeta_data.len() as u64 / 4096),
                    }],
                    ..Default::default()
                }],
                ..Default::default()
            },
        ],
        security_patch_level: Some("2026-04-05".to_string()),
        max_timestamp: Some(1744848000),
        ..Default::default()
    };

    let payload = build_payload_bin(&manifest, &[&boot_data, &vbmeta_data]);
    fs::write(dir.join("valid_full_ota.bin"), &payload).expect("Failed to write valid_full_ota.bin");

    println!(
        "    → {} bytes, boot hash={}, vbmeta hash={}",
        payload.len(),
        hex_encode(&boot_hash),
        hex_encode(&vbmeta_hash)
    );
}

// ---------------------------------------------------------------------------
// Fixture 2: Valid incremental payload.bin
// ---------------------------------------------------------------------------

/// Build a CrAU v2 payload.bin with minor_version=5 (incremental).
///
/// Contains 1 partition with a SOURCE_COPY operation to test the
/// incremental detection path and Experimental tier classification.
fn generate_valid_incremental(dir: &Path) {
    println!("  [2/6] valid_incremental.bin");

    let target_data = generate_partition_data(b"FORGE_INCR_TGT", 8192);
    let target_hash = sha256_bytes(&target_data);
    let source_hash = sha256_bytes(&generate_partition_data(b"FORGE_INCR_SRC", 8192));

    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(5), // Incremental
        partitions: vec![PartitionUpdate {
            partition_name: "system".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(8192),
                hash: Some(target_hash),
            }),
            old_partition_info: Some(PartitionInfo {
                size: Some(8192),
                hash: Some(source_hash),
            }),
            operations: vec![InstallOperation {
                r#type: OpType::SourceCopy as i32,
                src_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(2),
                }],
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(2),
                }],
                ..Default::default()
            }],
            version: Some("2026-04-05".to_string()),
            ..Default::default()
        }],
        security_patch_level: Some("2026-04-05".to_string()),
        ..Default::default()
    };

    // No data blobs needed for SOURCE_COPY — operations reference source partition
    let payload = build_payload_bin(&manifest, &[]);
    fs::write(dir.join("valid_incremental.bin"), &payload)
        .expect("Failed to write valid_incremental.bin");

    println!("    → {} bytes, incremental with SOURCE_COPY", payload.len());
}

// ---------------------------------------------------------------------------
// Fixture 3: ZIP-wrapped full OTA
// ---------------------------------------------------------------------------

/// Build a standard ZIP containing payload.bin + metadata entries.
///
/// This triggers the AospPayloadOta family detection path in sniff_seekable.
fn generate_ota_zip(dir: &Path) {
    println!("  [3/6] valid_ota.zip");

    let boot_data = generate_partition_data(b"FORGE_ZIP_BOOT", 32768);
    let boot_hash = sha256_bytes(&boot_data);

    let manifest = DeltaArchiveManifest {
        block_size: Some(4096),
        minor_version: Some(0),
        partitions: vec![PartitionUpdate {
            partition_name: "boot".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(boot_data.len() as u64),
                hash: Some(boot_hash),
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(boot_data.len() as u64),
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(boot_data.len() as u64 / 4096),
                }],
                ..Default::default()
            }],
            ..Default::default()
        }],
        security_patch_level: Some("2026-04-05".to_string()),
        ..Default::default()
    };

    let payload_bin = build_payload_bin(&manifest, &[&boot_data]);

    // Build ZIP with payload.bin + metadata stubs
    let zip_path = dir.join("valid_ota.zip");
    let file = fs::File::create(&zip_path).expect("Failed to create valid_ota.zip");
    let mut zip = zip::ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);

    zip.start_file("payload.bin", options)
        .expect("Failed to start payload.bin entry");
    zip.write_all(&payload_bin)
        .expect("Failed to write payload.bin");

    zip.start_file("payload_properties.txt", options)
        .expect("Failed to start properties entry");
    zip.write_all(b"FILE_HASH=abc\nFILE_SIZE=12345\nMETADATA_HASH=def\nMETADATA_SIZE=100\n")
        .expect("Failed to write properties");

    zip.start_file("META-INF/com/android/metadata", options)
        .expect("Failed to start metadata entry");
    zip.write_all(b"ota-type=AB\npre-device=sailfish\n")
        .expect("Failed to write metadata");

    zip.finish().expect("Failed to finalize ZIP");

    let zip_size = fs::metadata(&zip_path).map(|m| m.len()).unwrap_or(0);
    println!("    → {} bytes, ZIP with payload.bin", zip_size);
}

// ---------------------------------------------------------------------------
// Fixture 4: Legacy image ZIP (Forensic)
// ---------------------------------------------------------------------------

/// Build a ZIP with .img entries but no payload.bin.
///
/// Triggers LegacyImgZip classification → Forensic tier.
fn generate_legacy_img_zip(dir: &Path) {
    println!("  [4/6] legacy_images.zip");

    let zip_path = dir.join("legacy_images.zip");
    let file = fs::File::create(&zip_path).expect("Failed to create legacy_images.zip");
    let mut zip = zip::ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);

    // boot.img — small dummy
    zip.start_file("boot.img", options)
        .expect("Failed to start boot.img entry");
    zip.write_all(&[0xAA; 4096])
        .expect("Failed to write boot.img");

    // system.img — small dummy
    zip.start_file("system.img", options)
        .expect("Failed to start system.img entry");
    zip.write_all(&[0xBB; 8192])
        .expect("Failed to write system.img");

    zip.finish().expect("Failed to finalize legacy ZIP");

    let zip_size = fs::metadata(&zip_path).map(|m| m.len()).unwrap_or(0);
    println!("    → {} bytes, ZIP with .img entries only", zip_size);
}

// ---------------------------------------------------------------------------
// Fixture 5: Corrupted manifest
// ---------------------------------------------------------------------------

/// Build a payload.bin with valid header but garbage manifest bytes.
///
/// The manifest_size field points to random data that will fail
/// protobuf deserialization. Tests ManifestCorrupt error path.
fn generate_corrupted_manifest(dir: &Path) {
    println!("  [5/6] corrupted_manifest.bin");

    let garbage_manifest = vec![0xFF, 0xFE, 0xFD, 0xFC, 0xFB, 0xFA, 0xF9, 0xF8];
    let manifest_size = garbage_manifest.len() as u64;
    let sig_size = 0u32;

    let mut payload = Vec::new();
    // Valid CrAU v2 header
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&2u64.to_be_bytes());
    payload.extend_from_slice(&manifest_size.to_be_bytes());
    payload.extend_from_slice(&sig_size.to_be_bytes());
    // Garbage manifest data
    payload.extend_from_slice(&garbage_manifest);

    fs::write(dir.join("corrupted_manifest.bin"), &payload)
        .expect("Failed to write corrupted_manifest.bin");

    println!("    → {} bytes, valid header + garbage manifest", payload.len());
}

// ---------------------------------------------------------------------------
// Fixture 6: Truncated header
// ---------------------------------------------------------------------------

/// Build a payload that starts with valid CrAU magic but truncates
/// before the manifest bytes arrive.
///
/// Tests the HeaderTruncated / ManifestCorrupt error path when the
/// file is shorter than the header declares.
fn generate_truncated_header(dir: &Path) {
    println!("  [6/6] truncated_header.bin");

    let mut payload = Vec::new();
    // Valid CrAU magic
    payload.extend_from_slice(b"CrAU");
    // Valid version (2)
    payload.extend_from_slice(&2u64.to_be_bytes());
    // Manifest size that claims 1024 bytes...
    payload.extend_from_slice(&1024u64.to_be_bytes());
    // Metadata sig size
    payload.extend_from_slice(&0u32.to_be_bytes());
    // ...but only 4 bytes of manifest data follow (truncated!)
    payload.extend_from_slice(&[0x08, 0x01, 0x10, 0x00]);

    fs::write(dir.join("truncated_header.bin"), &payload)
        .expect("Failed to write truncated_header.bin");

    println!("    → {} bytes, header claims 1024B manifest but only 4B present", payload.len());
}

// ===========================================================================
// Helpers
// ===========================================================================

/// Generate deterministic partition data by repeating a pattern.
fn generate_partition_data(pattern: &[u8], size: usize) -> Vec<u8> {
    let mut data = Vec::with_capacity(size);
    while data.len() < size {
        let chunk = pattern.len().min(size - data.len());
        data.extend_from_slice(&pattern[..chunk]);
    }
    data.truncate(size);
    data
}

/// Compute SHA-256 digest of data, returning raw bytes.
fn sha256_bytes(data: &[u8]) -> Vec<u8> {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hasher.finalize().to_vec()
}

/// Build a complete payload.bin from a manifest and data blobs.
///
/// Layout: [CrAU magic][version u64][manifest_size u64][sig_size u32]
///         [manifest protobuf bytes][concatenated data blobs]
fn build_payload_bin(manifest: &DeltaArchiveManifest, data_blobs: &[&[u8]]) -> Vec<u8> {
    let manifest_bytes = manifest.encode_to_vec();
    let manifest_size = manifest_bytes.len() as u64;
    let sig_size = 0u32;

    let mut payload = Vec::new();
    // Header
    payload.extend_from_slice(b"CrAU");
    payload.extend_from_slice(&2u64.to_be_bytes());
    payload.extend_from_slice(&manifest_size.to_be_bytes());
    payload.extend_from_slice(&sig_size.to_be_bytes());
    // Manifest
    payload.extend_from_slice(&manifest_bytes);
    // Data blobs (concatenated in order — offsets in manifest reference these)
    for blob in data_blobs {
        payload.extend_from_slice(blob);
    }

    payload
}

/// Print a summary of all generated fixtures.
fn print_inventory(dir: &Path) {
    println!("\nCorpus inventory:");
    let mut total_size = 0u64;

    for entry in fs::read_dir(dir).expect("Failed to read output directory") {
        if let Ok(entry) = entry {
            if let Ok(meta) = entry.metadata() {
                let size = meta.len();
                total_size += size;
                println!(
                    "  {} — {} bytes",
                    entry.file_name().to_string_lossy(),
                    size
                );
            }
        }
    }

    println!("\nTotal corpus size: {} bytes ({:.1} KB)", total_size, total_size as f64 / 1024.0);
    assert!(
        total_size < 10 * 1024 * 1024,
        "Corpus exceeds 10 MB limit! Actual: {} bytes",
        total_size
    );
    println!("✓ Under 10 MB limit");
}
