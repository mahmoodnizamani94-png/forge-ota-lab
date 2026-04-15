//! Payload.bin parser and extraction operations.
//!
//! Handles CrAU-format payloads: header parsing, manifest deserialization,
//! partition iteration, and streaming decompression of InstallOperations.
//!
//! Architecture:
//! - `header` — CrAU binary header parsing with security bounds
//! - `proto` — prost-generated protobuf types (DeltaArchiveManifest, etc.)
//! - `manifest` — Manifest parsing and partition inventory construction
//! - `types` — Domain types for analysis and extraction results
//! - `error` — Rich error types with diagnostic context
//! - `analysis` — AnalysisResult construction for JNI serialization
//! - `extraction` — Streaming extraction engine with decompression

pub mod analysis;
pub mod error;
pub mod extraction;
pub mod header;
pub mod manifest;
pub mod proto;
pub mod types;

// Re-export key types at crate root for ergonomic imports
pub use analysis::AnalysisResult;
pub use error::{ExtractionError, PayloadError};
pub use extraction::{extract_partition, ExtractionOutcome, ProgressEvent};
pub use header::{parse_header, PayloadHeader};
pub use manifest::{parse_manifest, ParsedManifest};
pub use types::{CompressionAlgorithm, OperationSummary, PartitionSummary};
