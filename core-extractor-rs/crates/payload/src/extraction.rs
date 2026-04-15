//! Streaming extraction engine with decompression.
//!
//! Streams extraction for REPLACE, REPLACE_XZ, REPLACE_BZ, and ZERO operations,
//! writing to an output sink at extent-specified offsets with 256 KB streaming buffers.
//!
//! Security invariants enforced per-chunk:
//! - Decompression bomb: bytes_written > declared_size × 1.01 → abort
//! - Integer overflow: all extent calculations use checked_mul/checked_add
//! - Cancellation: AtomicBool checked every loop iteration
//!
//! WHY streaming: PRD FR-3 requires "never buffer full partition in memory."
//! We read compressed data and decompress in 256 KB chunks.

use crate::error::ExtractionError;
use crate::proto::{OpType, PartitionUpdate};
use forge_verification::StreamingHasher;
use serde::Serialize;
use std::io::{Read, Seek, SeekFrom, Write};
use std::sync::atomic::{AtomicBool, Ordering};

/// Streaming buffer size: 256 KB.
/// PRD: "Extraction reads and writes in 256 KB chunks."
const STREAM_BUFFER_SIZE: usize = 256 * 1024;

/// Zero-fill buffer (pre-allocated for ZERO operations).
const ZERO_BUFFER_SIZE: usize = 64 * 1024;

/// Decompression bomb ratio: 1.01 (101%).
/// PRD Security: "Abort if bytes_written > declared_size × 1.01."
const DECOMPRESSION_BOMB_RATIO_NUMERATOR: u64 = 101;
const DECOMPRESSION_BOMB_RATIO_DENOMINATOR: u64 = 100;

// ---------------------------------------------------------------------------
// Progress and outcome types
// ---------------------------------------------------------------------------

/// Progress event emitted during extraction.
#[derive(Debug, Clone, Serialize)]
pub struct ProgressEvent {
    /// Partition name being extracted.
    pub partition: String,
    /// Current operation index (0-based).
    pub operation_index: usize,
    /// Total operations for this partition.
    pub total_operations: usize,
    /// Bytes written so far for this partition.
    pub bytes_written: u64,
    /// Total expected bytes for this partition.
    pub total_bytes: u64,
}

/// Outcome of extracting a single partition.
#[derive(Debug, Clone, Serialize)]
pub struct ExtractionOutcome {
    /// Partition name.
    pub partition: String,
    /// Total bytes extracted.
    pub bytes_extracted: u64,
    /// SHA-256 hex digest of the extracted output.
    pub sha256: String,
    /// Number of operations executed.
    pub operations_executed: usize,
}

// ---------------------------------------------------------------------------
// Public extraction API
// ---------------------------------------------------------------------------

/// Extract a single partition from a payload.bin file.
///
/// Iterates InstallOperations from the PartitionUpdate, applying each
/// in order: read compressed data blob, decompress, write to output at
/// extent-specified offsets.
///
/// Security:
/// - Decompression bomb: checked after every decompress write
/// - Integer overflow: checked_mul/checked_add on all extent calculations
/// - Cancellation: AtomicBool checked before each operation
///
/// # Panics
/// Never panics — all errors are returned as `ExtractionError`.
pub fn extract_partition<R, W, F>(
    source: &mut R,
    output: &mut W,
    partition: &PartitionUpdate,
    data_offset: u64,
    block_size: u32,
    cancel_token: &AtomicBool,
    mut progress_callback: F,
) -> Result<ExtractionOutcome, ExtractionError>
where
    R: Read + Seek,
    W: Write + Seek,
    F: FnMut(ProgressEvent),
{
    let partition_name = &partition.partition_name;

    // Determine declared partition size for decompression bomb detection
    let declared_size = partition
        .new_partition_info
        .as_ref()
        .and_then(|info| info.size)
        .unwrap_or(u64::MAX);

    // Calculate decompression bomb limit using checked arithmetic
    let bomb_limit = declared_size
        .checked_mul(DECOMPRESSION_BOMB_RATIO_NUMERATOR)
        .and_then(|v| v.checked_div(DECOMPRESSION_BOMB_RATIO_DENOMINATOR))
        .unwrap_or(u64::MAX);

    let mut hasher = StreamingHasher::new();
    let mut total_bytes_written: u64 = 0;
    let total_operations = partition.operations.len();

    for (op_index, op) in partition.operations.iter().enumerate() {
        // Check cancellation before each operation
        if cancel_token.load(Ordering::Relaxed) {
            return Err(ExtractionError::Cancelled);
        }

        // Emit progress
        progress_callback(ProgressEvent {
            partition: partition_name.clone(),
            operation_index: op_index,
            total_operations,
            bytes_written: total_bytes_written,
            total_bytes: declared_size,
        });

        // Execute the operation
        execute_operation(
            source,
            output,
            op,
            data_offset,
            block_size,
            &mut hasher,
            &mut total_bytes_written,
            bomb_limit,
            partition_name,
            op_index,
        )?;
    }

    let sha256 = hasher.finalize_hex();

    Ok(ExtractionOutcome {
        partition: partition_name.clone(),
        bytes_extracted: total_bytes_written,
        sha256,
        operations_executed: total_operations,
    })
}

// ---------------------------------------------------------------------------
// Operation execution
// ---------------------------------------------------------------------------

/// Execute a single InstallOperation.
#[allow(clippy::too_many_arguments)]
fn execute_operation<R, W>(
    source: &mut R,
    output: &mut W,
    op: &crate::proto::InstallOperation,
    data_offset: u64,
    block_size: u32,
    hasher: &mut StreamingHasher,
    bytes_written: &mut u64,
    bomb_limit: u64,
    partition_name: &str,
    op_index: usize,
) -> Result<(), ExtractionError>
where
    R: Read + Seek,
    W: Write + Seek,
{
    let op_type = OpType::try_from(op.r#type).map_err(|_| {
        ExtractionError::UnsupportedOperation {
            op_type: op.r#type,
            partition: partition_name.to_string(),
        }
    })?;

    match op_type {
        OpType::Replace => {
            execute_replace(
                source,
                output,
                op,
                data_offset,
                block_size,
                hasher,
                bytes_written,
                bomb_limit,
                partition_name,
                op_index,
                None, // no decompression
            )
        }
        OpType::ReplaceXz => {
            execute_replace(
                source,
                output,
                op,
                data_offset,
                block_size,
                hasher,
                bytes_written,
                bomb_limit,
                partition_name,
                op_index,
                Some(DecompressType::Xz),
            )
        }
        OpType::ReplaceBz => {
            execute_replace(
                source,
                output,
                op,
                data_offset,
                block_size,
                hasher,
                bytes_written,
                bomb_limit,
                partition_name,
                op_index,
                Some(DecompressType::Bzip2),
            )
        }
        OpType::Zero => execute_zero(output, op, block_size, hasher, bytes_written, bomb_limit, partition_name),
        OpType::Discard => {
            // DISCARD: skip or write zeros on filesystems without hole support.
            // For portable extraction, we treat DISCARD as a no-op.
            Ok(())
        }
        // Source-dependent operations (incremental) — not supported in full OTA extraction
        OpType::SourceCopy | OpType::SourceBsdiff | OpType::Bsdiff | OpType::Puffdiff
        | OpType::BrotliBsdiff | OpType::Zucchini | OpType::Lz4diffBsdiff
        | OpType::Lz4diffPuffdiff => {
            Err(ExtractionError::UnsupportedOperation {
                op_type: op.r#type,
                partition: partition_name.to_string(),
            })
        }
        // MOVE is deprecated
        OpType::Move => Err(ExtractionError::UnsupportedOperation {
            op_type: op.r#type,
            partition: partition_name.to_string(),
        }),
    }
}

enum DecompressType {
    Xz,
    Bzip2,
}

/// Execute a REPLACE / REPLACE_XZ / REPLACE_BZ operation.
///
/// Reads the compressed (or raw) data blob from the source at the specified
/// offset, optionally decompresses it, then writes to output at the destination
/// extents.
#[allow(clippy::too_many_arguments)]
fn execute_replace<R, W>(
    source: &mut R,
    output: &mut W,
    op: &crate::proto::InstallOperation,
    data_offset: u64,
    block_size: u32,
    hasher: &mut StreamingHasher,
    bytes_written: &mut u64,
    bomb_limit: u64,
    partition_name: &str,
    op_index: usize,
    decompress: Option<DecompressType>,
) -> Result<(), ExtractionError>
where
    R: Read + Seek,
    W: Write + Seek,
{
    let op_data_offset = op.data_offset.unwrap_or(0);
    let op_data_length = op.data_length.unwrap_or(0);

    // Calculate absolute offset in the payload file using checked arithmetic
    // PRD Security: "All extent calculations use checked_mul/checked_add."
    let absolute_offset = data_offset
        .checked_add(op_data_offset)
        .ok_or_else(|| ExtractionError::IntegerOverflow {
            context: format!(
                "data_offset ({data_offset}) + op.data_offset ({op_data_offset}) overflows u64"
            ),
        })?;

    // Seek to the data blob in the source
    source
        .seek(SeekFrom::Start(absolute_offset))
        .map_err(|e| ExtractionError::Io {
            partition: partition_name.to_string(),
            context: format!("seeking to data blob at offset {absolute_offset}"),
            source: e,
        })?;

    // Seek output to the destination extent
    seek_to_extent(output, &op.dst_extents, block_size, partition_name)?;

    // Read the compressed/raw data blob
    let blob_reader = source.take(op_data_length);

    // Decompress and write
    match decompress {
        None => {
            // Raw REPLACE: copy directly
            stream_copy(blob_reader, output, hasher, bytes_written, bomb_limit, partition_name)?;
        }
        Some(DecompressType::Xz) => {
            let decoder = xz2::read::XzDecoder::new(blob_reader);
            stream_copy(decoder, output, hasher, bytes_written, bomb_limit, partition_name)?;
        }
        Some(DecompressType::Bzip2) => {
            let decoder = bzip2::read::BzDecoder::new(blob_reader);
            stream_copy(decoder, output, hasher, bytes_written, bomb_limit, partition_name)?;
        }
    }

    let _ = op_index; // used in error context if needed

    Ok(())
}

/// Execute a ZERO operation: write zeros to destination extents.
fn execute_zero<W>(
    output: &mut W,
    op: &crate::proto::InstallOperation,
    block_size: u32,
    hasher: &mut StreamingHasher,
    bytes_written: &mut u64,
    bomb_limit: u64,
    partition_name: &str,
) -> Result<(), ExtractionError>
where
    W: Write + Seek,
{
    let zeros = [0u8; ZERO_BUFFER_SIZE];

    for extent in &op.dst_extents {
        let start_block = extent.start_block.unwrap_or(0);
        let num_blocks = extent.num_blocks.unwrap_or(0);

        // Calculate byte offset using checked arithmetic
        let offset = start_block
            .checked_mul(block_size as u64)
            .ok_or_else(|| ExtractionError::IntegerOverflow {
                context: format!(
                    "start_block ({start_block}) × block_size ({block_size}) overflows u64"
                ),
            })?;

        let total_bytes = num_blocks
            .checked_mul(block_size as u64)
            .ok_or_else(|| ExtractionError::IntegerOverflow {
                context: format!(
                    "num_blocks ({num_blocks}) × block_size ({block_size}) overflows u64"
                ),
            })?;

        output
            .seek(SeekFrom::Start(offset))
            .map_err(|e| ExtractionError::Io {
                partition: partition_name.to_string(),
                context: format!("seeking to zero extent at offset {offset}"),
                source: e,
            })?;

        let mut remaining = total_bytes;
        while remaining > 0 {
            let chunk = (remaining as usize).min(ZERO_BUFFER_SIZE);
            let buf = &zeros[..chunk];

            output.write_all(buf).map_err(|e| ExtractionError::Io {
                partition: partition_name.to_string(),
                context: "writing zero bytes".to_string(),
                source: e,
            })?;

            hasher.update(buf);
            *bytes_written += chunk as u64;
            remaining -= chunk as u64;

            // Decompression bomb check (applies to zero operations too)
            check_bomb_limit(*bytes_written, bomb_limit, partition_name)?;
        }
    }

    Ok(())
}

// ---------------------------------------------------------------------------
// Streaming helpers
// ---------------------------------------------------------------------------

/// Seek the output writer to the first destination extent.
fn seek_to_extent<W: Seek>(
    output: &mut W,
    extents: &[crate::proto::Extent],
    block_size: u32,
    partition_name: &str,
) -> Result<(), ExtractionError> {
    if let Some(first_extent) = extents.first() {
        let start_block = first_extent.start_block.unwrap_or(0);
        let offset = start_block
            .checked_mul(block_size as u64)
            .ok_or_else(|| ExtractionError::IntegerOverflow {
                context: format!(
                    "extent start_block ({start_block}) × block_size ({block_size}) overflows"
                ),
            })?;

        output
            .seek(SeekFrom::Start(offset))
            .map_err(|e| ExtractionError::Io {
                partition: partition_name.to_string(),
                context: format!("seeking to extent at block {start_block}"),
                source: e,
            })?;
    }
    Ok(())
}

/// Stream data from reader to writer in STREAM_BUFFER_SIZE chunks,
/// updating the hasher and checking the decompression bomb limit.
fn stream_copy<R: Read, W: Write>(
    mut reader: R,
    writer: &mut W,
    hasher: &mut StreamingHasher,
    bytes_written: &mut u64,
    bomb_limit: u64,
    partition_name: &str,
) -> Result<(), ExtractionError> {
    let mut buf = vec![0u8; STREAM_BUFFER_SIZE];

    loop {
        let n = reader.read(&mut buf).map_err(|e| {
            ExtractionError::DecompressFailed {
                algorithm: "stream read".to_string(),
                details: e.to_string(),
            }
        })?;

        if n == 0 {
            break;
        }

        writer.write_all(&buf[..n]).map_err(|e| ExtractionError::Io {
            partition: partition_name.to_string(),
            context: "writing decompressed data".to_string(),
            source: e,
        })?;

        hasher.update(&buf[..n]);
        *bytes_written += n as u64;

        // PRD Security: decompression bomb check per chunk, not at end
        check_bomb_limit(*bytes_written, bomb_limit, partition_name)?;
    }

    Ok(())
}

/// Check if bytes_written exceeds the decompression bomb limit.
///
/// PRD Security: "Abort if bytes_written > declared_size × 1.01.
/// Checked per chunk, not at end."
fn check_bomb_limit(
    bytes_written: u64,
    bomb_limit: u64,
    _partition_name: &str,
) -> Result<(), ExtractionError> {
    if bytes_written > bomb_limit && bomb_limit < u64::MAX {
        return Err(ExtractionError::DecompressionBomb {
            actual: bytes_written,
            declared: bomb_limit
                .checked_mul(DECOMPRESSION_BOMB_RATIO_DENOMINATOR)
                .unwrap_or(0)
                / DECOMPRESSION_BOMB_RATIO_NUMERATOR,
            limit: bomb_limit,
        });
    }
    Ok(())
}

// ===========================================================================
// Tests
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{Extent, InstallOperation, PartitionInfo, PartitionUpdate};
    use std::io::Cursor;
    use std::sync::atomic::AtomicBool;

    /// Create a partition with a single REPLACE operation.
    fn make_replace_partition(name: &str, data: &[u8]) -> (PartitionUpdate, Vec<u8>) {
        let data_len = data.len() as u64;
        let block_size = 4096u32;
        let num_blocks = (data_len + block_size as u64 - 1) / block_size as u64;

        let partition = PartitionUpdate {
            partition_name: name.to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(data_len),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(data_len),
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(num_blocks),
                }],
                ..Default::default()
            }],
            ..Default::default()
        };

        (partition, data.to_vec())
    }

    #[test]
    fn test_extract_replace_operation_produces_correct_output() {
        let test_data = b"Hello, extraction world! This is a test.";
        let (partition, source_data) = make_replace_partition("test_partition", test_data);

        let mut source = Cursor::new(source_data);
        let mut output = Cursor::new(Vec::new());
        let cancel = AtomicBool::new(false);
        let mut progress_count = 0;

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0, // data_offset
            4096,
            &cancel,
            |_| progress_count += 1,
        )
        .unwrap();

        assert_eq!(result.partition, "test_partition");
        assert_eq!(result.bytes_extracted, test_data.len() as u64);
        assert!(!result.sha256.is_empty());
        assert_eq!(result.operations_executed, 1);
        assert!(progress_count > 0);

        // Verify output content
        let output_bytes = output.into_inner();
        assert_eq!(&output_bytes[..test_data.len()], test_data);
    }

    #[test]
    fn test_extract_zero_operation_writes_zeros() {
        let partition = PartitionUpdate {
            partition_name: "zero_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(8192),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Zero as i32,
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(2), // 2 blocks × 4096 = 8192 bytes
                }],
                ..Default::default()
            }],
            ..Default::default()
        };

        let mut source = Cursor::new(Vec::new());
        let mut output = Cursor::new(Vec::new());
        let cancel = AtomicBool::new(false);

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |_| {},
        )
        .unwrap();

        assert_eq!(result.bytes_extracted, 8192);
        let output_bytes = output.into_inner();
        assert!(output_bytes.iter().all(|&b| b == 0));
    }

    #[test]
    fn test_cancellation_stops_extraction() {
        let test_data = vec![0u8; 65536];
        let (mut partition, source_data) = make_replace_partition("cancel_test", &test_data);

        // Add many operations so cancellation has a chance to trigger
        for i in 1..10 {
            partition.operations.push(InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(65536),
                dst_extents: vec![Extent {
                    start_block: Some(i * 16),
                    num_blocks: Some(16),
                }],
                ..Default::default()
            });
        }

        let mut source = Cursor::new(source_data);
        let mut output = Cursor::new(vec![0u8; 1024 * 1024]);
        let cancel = AtomicBool::new(true); // pre-cancelled

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |_| {},
        );

        assert!(matches!(result, Err(ExtractionError::Cancelled)));
    }

    #[test]
    fn test_decompression_bomb_fires_at_101_percent() {
        // Create a partition that claims to be 100 bytes but we'll write 102+ bytes
        let partition = PartitionUpdate {
            partition_name: "bomb_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(100), // declared 100 bytes
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Replace as i32,
                data_offset: Some(0),
                data_length: Some(102), // slightly over 101%
                dst_extents: vec![Extent {
                    start_block: Some(0),
                    num_blocks: Some(1),
                }],
                ..Default::default()
            }],
            ..Default::default()
        };

        // Source data larger than declared
        let source_data = vec![0x42u8; 102];
        let mut source = Cursor::new(source_data);
        let mut output = Cursor::new(vec![0u8; 1024]);
        let cancel = AtomicBool::new(false);

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |_| {},
        );

        assert!(matches!(
            result,
            Err(ExtractionError::DecompressionBomb { .. })
        ));
    }

    #[test]
    fn test_checked_arithmetic_rejects_overflow() {
        let partition = PartitionUpdate {
            partition_name: "overflow_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(4096),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::Zero as i32,
                dst_extents: vec![Extent {
                    start_block: Some(u64::MAX), // will overflow when × block_size
                    num_blocks: Some(1),
                }],
                ..Default::default()
            }],
            ..Default::default()
        };

        let mut source = Cursor::new(Vec::new());
        let mut output = Cursor::new(vec![0u8; 4096]);
        let cancel = AtomicBool::new(false);

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |_| {},
        );

        assert!(matches!(
            result,
            Err(ExtractionError::IntegerOverflow { .. })
        ));
    }

    #[test]
    fn test_unsupported_operation_returns_error() {
        let partition = PartitionUpdate {
            partition_name: "unsupported_test".to_string(),
            new_partition_info: Some(PartitionInfo {
                size: Some(4096),
                hash: None,
            }),
            operations: vec![InstallOperation {
                r#type: OpType::SourceCopy as i32, // requires source partition
                ..Default::default()
            }],
            ..Default::default()
        };

        let mut source = Cursor::new(Vec::new());
        let mut output = Cursor::new(vec![0u8; 4096]);
        let cancel = AtomicBool::new(false);

        let result = extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |_| {},
        );

        assert!(matches!(
            result,
            Err(ExtractionError::UnsupportedOperation { .. })
        ));
    }

    #[test]
    fn test_extraction_outcome_serializes_to_json() {
        let outcome = ExtractionOutcome {
            partition: "boot".to_string(),
            bytes_extracted: 65536,
            sha256: "abcdef1234567890".to_string(),
            operations_executed: 5,
        };
        let json = serde_json::to_string(&outcome).unwrap();
        assert!(json.contains("boot"));
        assert!(json.contains("65536"));
        assert!(json.contains("abcdef1234567890"));
    }

    #[test]
    fn test_progress_callback_receives_events() {
        let test_data = vec![0x42u8; 4096];
        let (partition, source_data) = make_replace_partition("progress_test", &test_data);

        let mut source = Cursor::new(source_data);
        let mut output = Cursor::new(Vec::new());
        let cancel = AtomicBool::new(false);
        let mut events: Vec<ProgressEvent> = Vec::new();

        extract_partition(
            &mut source,
            &mut output,
            &partition,
            0,
            4096,
            &cancel,
            |e| events.push(e),
        )
        .unwrap();

        assert!(!events.is_empty());
        assert_eq!(events[0].partition, "progress_test");
        assert_eq!(events[0].operation_index, 0);
        assert_eq!(events[0].total_operations, 1);
    }
}
