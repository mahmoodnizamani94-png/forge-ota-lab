//! Standard payload.bin adapter.
//!
//! Handles Google/Pixel and standard payload-based full OTAs.
//! This is the primary launch adapter — it covers the majority of
//! modern Android A/B OTA packages.

use crate::{MatchConfidence, OtaAdapter, ReadSeek};
use forge_payload::analysis::{analyze, AnalysisResult};
use forge_payload::error::PayloadError;
use forge_sniff::{PackageFamily, SupportTier, detect_magic, MagicBytes};

/// Standard payload.bin adapter for AOSP-compatible OTA packages.
///
/// Supports:
/// - Google Pixel full OTAs (Supported tier)
/// - Standard payload.bin families using compatible update_engine semantics (Supported)
/// - Standalone payload.bin files (Supported)
/// - Incremental payloads (Experimental tier)
pub struct PayloadAdapter;

impl PayloadAdapter {
    /// Create a new PayloadAdapter instance.
    pub fn new() -> Self {
        Self
    }
}

impl Default for PayloadAdapter {
    fn default() -> Self {
        Self::new()
    }
}

impl OtaAdapter for PayloadAdapter {
    fn id(&self) -> &str {
        "standard_payload"
    }

    fn family(&self) -> &str {
        "Standard Payload"
    }

    fn version(&self) -> &str {
        env!("CARGO_PKG_VERSION")
    }

    fn sniff(&self, header: &[u8]) -> MatchConfidence {
        let magic = detect_magic(header);
        match magic {
            MagicBytes::CrauPayload => MatchConfidence::Definitive,
            MagicBytes::Zip => {
                // ZIP could contain payload.bin — needs deeper scan.
                // Return Possible; the registry will use sniff_seekable for full check.
                MatchConfidence::Possible
            }
            _ => MatchConfidence::NoMatch,
        }
    }

    fn analyze(
        &self,
        reader: &mut dyn ReadSeek,
    ) -> Result<AnalysisResult, PayloadError> {
        // Determine family from sniffing the header
        let mut header = [0u8; 4096];
        let bytes_read = reader.read(&mut header).map_err(|e| PayloadError::Io {
            context: "reading header for adapter analysis".to_string(),
            source: e,
        })?;

        let magic = detect_magic(&header[..bytes_read]);
        let (family, tier) = match magic {
            MagicBytes::CrauPayload => (PackageFamily::StandalonePayload, SupportTier::Supported),
            MagicBytes::Zip => (PackageFamily::AospPayloadOta, SupportTier::Supported),
            _ => (PackageFamily::Unknown, SupportTier::Forensic),
        };

        // Seek back to start for full analysis
        reader
            .seek(std::io::SeekFrom::Start(0))
            .map_err(|e| PayloadError::Io {
                context: "seeking back to start for analysis".to_string(),
                source: e,
            })?;

        analyze(reader, family, tier)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_payload_adapter_id_and_family() {
        let adapter = PayloadAdapter::new();
        assert_eq!(adapter.id(), "standard_payload");
        assert_eq!(adapter.family(), "Standard Payload");
        assert!(!adapter.version().is_empty());
    }

    #[test]
    fn test_payload_adapter_sniffs_crau_as_definitive() {
        let adapter = PayloadAdapter::new();
        let header = b"CrAU\x00\x00\x00\x00";
        assert_eq!(adapter.sniff(header), MatchConfidence::Definitive);
    }

    #[test]
    fn test_payload_adapter_sniffs_zip_as_possible() {
        let adapter = PayloadAdapter::new();
        let header = [0x50, 0x4B, 0x03, 0x04, 0x00, 0x00];
        assert_eq!(adapter.sniff(&header), MatchConfidence::Possible);
    }

    #[test]
    fn test_payload_adapter_sniffs_unknown_as_no_match() {
        let adapter = PayloadAdapter::new();
        let header = [0xFF; 8];
        assert_eq!(adapter.sniff(&header), MatchConfidence::NoMatch);
    }
}
