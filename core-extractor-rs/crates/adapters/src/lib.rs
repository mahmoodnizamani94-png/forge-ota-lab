//! OTA family adapters and registry.
//!
//! Each adapter encapsulates family-specific extraction logic:
//! sniffing, analysis, base validation, and partition extraction.
//! The adapter registry matches packages to the correct adapter
//! and determines support tier.
//!
//! v1 launch adapters:
//! - `PayloadAdapter`: Google/Pixel and standard payload.bin families

mod payload_adapter;
mod registry;

pub use payload_adapter::PayloadAdapter;
pub use registry::AdapterRegistry;

use forge_payload::AnalysisResult;
use forge_payload::error::PayloadError;
use forge_sniff::SupportTier;
use serde::Serialize;
use std::io::{Read, Seek};

// ---------------------------------------------------------------------------
// Adapter trait
// ---------------------------------------------------------------------------

/// Confidence level for adapter matching.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
pub enum MatchConfidence {
    /// Adapter does not match this package.
    NoMatch,
    /// Adapter might match — low confidence.
    Possible,
    /// Adapter matches with high confidence.
    Confident,
    /// Adapter definitively matches (e.g., magic bytes + manifest structure).
    Definitive,
}

/// Result of matching an adapter to a package.
#[derive(Debug, Clone, Serialize)]
pub struct MatchResult {
    pub adapter_id: String,
    pub family: String,
    pub confidence: MatchConfidence,
    pub tier: SupportTier,
}

/// Helper trait combining Read + Seek for dyn compatibility.
pub trait ReadSeek: Read + Seek {}
impl<T: Read + Seek> ReadSeek for T {}

/// Trait for OTA family adapters.
///
/// Each OTA family (Pixel, Samsung, etc.) implements this trait.
/// The registry selects the best-matching adapter for a given package.
///
/// WHY trait objects over generics: The registry stores `Box<dyn OtaAdapter>`,
/// which requires dyn compatibility. Using a concrete `&mut dyn ReadSeek`
/// instead of generic `R: Read + Seek` enables this.
pub trait OtaAdapter: Send + Sync {
    /// Unique identifier for this adapter.
    fn id(&self) -> &str;

    /// Human-readable family name.
    fn family(&self) -> &str;

    /// Adapter version string.
    fn version(&self) -> &str;

    /// Match this adapter against a file header (first 4 KB).
    fn sniff(&self, header: &[u8]) -> MatchConfidence;

    /// Analyze the package and return a full analysis result.
    fn analyze(&self, reader: &mut dyn ReadSeek) -> Result<AnalysisResult, PayloadError>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_match_confidence_ordering() {
        assert!(MatchConfidence::Definitive > MatchConfidence::Confident);
        assert!(MatchConfidence::Confident > MatchConfidence::Possible);
        assert!(MatchConfidence::Possible > MatchConfidence::NoMatch);
    }

    #[test]
    fn test_match_result_serializes() {
        let result = MatchResult {
            adapter_id: "pixel_payload".to_string(),
            family: "Google Pixel".to_string(),
            confidence: MatchConfidence::Definitive,
            tier: SupportTier::Supported,
        };
        let json = serde_json::to_string(&result).unwrap();
        assert!(json.contains("pixel_payload"));
        assert!(json.contains("Definitive"));
    }
}
