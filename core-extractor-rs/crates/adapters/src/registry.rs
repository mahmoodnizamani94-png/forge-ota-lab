//! Adapter registry — matches packages to the best adapter.
//!
//! The registry holds all registered adapters and selects the best match
//! for a given package header. Match selection uses confidence ordering:
//! Definitive > Confident > Possible > NoMatch.

use crate::{MatchConfidence, MatchResult, OtaAdapter, PayloadAdapter};
use forge_sniff::SupportTier;

/// Registry of OTA family adapters.
///
/// Holds all registered adapters and provides the matching interface.
/// v1 ships with the standard payload adapter. Additional adapters
/// (Samsung, etc.) will be added via signed adapter manifests.
pub struct AdapterRegistry {
    adapters: Vec<Box<dyn OtaAdapter>>,
}

impl AdapterRegistry {
    /// Create a new registry with the default v1 launch adapters.
    pub fn new() -> Self {
        let mut registry = Self {
            adapters: Vec::new(),
        };
        // Register the standard payload adapter (Pixel + compatible OEMs)
        registry.register(Box::new(PayloadAdapter::new()));
        registry
    }

    /// Register a new adapter.
    pub fn register(&mut self, adapter: Box<dyn OtaAdapter>) {
        self.adapters.push(adapter);
    }

    /// Match a file header against all registered adapters.
    ///
    /// Returns the best match (highest confidence), or None if no adapter
    /// matches with at least `Possible` confidence.
    ///
    /// PRD FR-2: "Registry lookup by family must complete in ≤ 50 ms."
    /// This is trivially met since we iterate a small fixed list.
    pub fn match_adapters(&self, header: &[u8]) -> Option<MatchResult> {
        let mut best: Option<(MatchConfidence, MatchResult)> = None;

        for adapter in &self.adapters {
            let confidence = adapter.sniff(header);

            if confidence > MatchConfidence::NoMatch {
                let tier = match confidence {
                    MatchConfidence::Definitive | MatchConfidence::Confident => {
                        SupportTier::Supported
                    }
                    MatchConfidence::Possible => SupportTier::Experimental,
                    MatchConfidence::NoMatch => unreachable!(),
                };

                let result = MatchResult {
                    adapter_id: adapter.id().to_string(),
                    family: adapter.family().to_string(),
                    confidence,
                    tier,
                };

                match &best {
                    Some((best_confidence, _)) if confidence > *best_confidence => {
                        best = Some((confidence, result));
                    }
                    None => {
                        best = Some((confidence, result));
                    }
                    _ => {}
                }
            }
        }

        best.map(|(_, result)| result)
    }

    /// Get a reference to a registered adapter by ID.
    pub fn get_adapter(&self, id: &str) -> Option<&dyn OtaAdapter> {
        self.adapters
            .iter()
            .find(|a| a.id() == id)
            .map(|a| a.as_ref())
    }

    /// Number of registered adapters.
    pub fn adapter_count(&self) -> usize {
        self.adapters.len()
    }
}

impl Default for AdapterRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_registry_starts_with_payload_adapter() {
        let registry = AdapterRegistry::new();
        assert_eq!(registry.adapter_count(), 1);
    }

    #[test]
    fn test_registry_matches_crau_header() {
        let registry = AdapterRegistry::new();
        let header = b"CrAU\x00\x00\x00\x02\x00\x00\x00\x00\x00\x00\x04\x00";
        let result = registry.match_adapters(header);

        assert!(result.is_some());
        let m = result.unwrap();
        assert_eq!(m.adapter_id, "standard_payload");
        assert_eq!(m.confidence, MatchConfidence::Definitive);
        assert_eq!(m.tier, SupportTier::Supported);
    }

    #[test]
    fn test_registry_matches_zip_header_as_possible() {
        let registry = AdapterRegistry::new();
        let header = [0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00];
        let result = registry.match_adapters(&header);

        assert!(result.is_some());
        let m = result.unwrap();
        assert_eq!(m.confidence, MatchConfidence::Possible);
    }

    #[test]
    fn test_registry_returns_none_for_unknown_format() {
        let registry = AdapterRegistry::new();
        let header = [0xFF; 16];
        let result = registry.match_adapters(&header);

        assert!(result.is_none());
    }

    #[test]
    fn test_get_adapter_by_id() {
        let registry = AdapterRegistry::new();
        let adapter = registry.get_adapter("standard_payload");
        assert!(adapter.is_some());
        assert_eq!(adapter.unwrap().family(), "Standard Payload");
    }

    #[test]
    fn test_get_adapter_missing_id_returns_none() {
        let registry = AdapterRegistry::new();
        assert!(registry.get_adapter("nonexistent").is_none());
    }
}
