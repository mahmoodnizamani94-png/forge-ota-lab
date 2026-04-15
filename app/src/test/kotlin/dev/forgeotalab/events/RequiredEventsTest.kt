package dev.forgeotalab.events

import com.google.common.truth.Truth.assertThat
import dev.forgeotalab.contracts.events.ForgeEvent
import org.junit.Test

/**
 * Structural verification that every PRD Required Event has a corresponding
 * ForgeEvent subclass.
 *
 * This test uses reflection to enumerate all ForgeEvent sealed subclasses
 * and verifies that the PRD's Required Events list is fully covered.
 *
 * WHY reflective: Adding a new event to the PRD without adding the
 * corresponding ForgeEvent subclass would be caught at compile time
 * (the when statement in the event logger would fail to compile).
 * This test goes further — it verifies the INVENTORY is complete.
 */
class RequiredEventsTest {

    /**
     * PRD Required Events (§865) — every event in this list must have
     * a corresponding ForgeEvent sealed subclass.
     *
     * This list is maintained manually and must be updated when new
     * PRD events are added.
     */
    private val requiredPrdEvents = listOf(
        // Analysis phase
        "AnalysisStarted",
        "AnalysisCompleted",
        "AnalysisFailed",

        // Extraction phase
        "ExtractionStarted",
        "ExtractionPhaseChanged",
        "ExtractionCompleted",
        "PartitionExtracted",

        // Verification
        "ArtifactVerified",
        "ArtifactVerifyFailed",

        // Concurrency
        "ConcurrentJobBlocked",

        // Incremental prerequisites
        "IncrementalPrereqBlocked",
        "IncrementalPrereqSatisfied",
        "IncrementalBaseMismatch",

        // Filesystem browser
        "FilesystemBrowserOpened",
        "FilesystemExported",
        "FilesystemUnsupported",

        // Job persistence
        "JobResumedAfterInterruption",
        "JobRecoveryFailed",
        "JobCleanupCompleted",

        // History
        "HistoryOpened",
        "HistoryReopenFailed",
        "HistoryItemRemoved",

        // Diagnostics
        "DiagnosticsExported",
        "FormatReportExported",

        // Adapter manifest
        "AdapterRegistryLoaded",
        "AdapterManifestRefreshed",
        "AdapterRegistryRefreshFailed",
        "AdapterManifestSignatureFailed",
        "AdapterRevocationApplied",
    )

    @Test
    fun all_prd_required_events_have_forge_event_subclass() {
        // Get all sealed subclasses of ForgeEvent
        val sealedSubclasses = ForgeEvent::class.sealedSubclasses.map { it.simpleName }

        for (eventName in requiredPrdEvents) {
            assertThat(sealedSubclasses)
                .withMessage("PRD Required Event '$eventName' missing from ForgeEvent sealed class")
                .contains(eventName)
        }
    }

    @Test
    fun forge_event_subclasses_all_have_timestamp() {
        // Every ForgeEvent subclass must have a timestampMs property
        val subclasses = ForgeEvent::class.sealedSubclasses

        for (subclass in subclasses) {
            val hasTimestamp = subclass.members.any { it.name == "timestampMs" }
            assertThat(hasTimestamp)
                .withMessage("${subclass.simpleName} must have 'timestampMs' property")
                .isTrue()
        }
    }

    @Test
    fun no_unexpected_orphan_events() {
        // Every ForgeEvent subclass should be in the required events list.
        // If a new event is added to ForgeEvent but NOT to the PRD list,
        // this test flags it for review.
        val sealedSubclasses = ForgeEvent::class.sealedSubclasses.mapNotNull { it.simpleName }
        val requiredSet = requiredPrdEvents.toSet()

        for (subclass in sealedSubclasses) {
            assertThat(requiredSet)
                .withMessage(
                    "ForgeEvent.$subclass exists but is not in the PRD Required Events list. " +
                        "Add it to requiredPrdEvents or remove the subclass."
                )
                .contains(subclass)
        }
    }

    @Test
    fun event_count_matches_prd_specification() {
        val sealedCount = ForgeEvent::class.sealedSubclasses.size
        val prdCount = requiredPrdEvents.size

        assertThat(sealedCount)
            .withMessage(
                "ForgeEvent has $sealedCount subclasses but PRD specifies $prdCount events. " +
                    "Sync requiredPrdEvents list with ForgeEvent sealed class."
            )
            .isEqualTo(prdCount)
    }
}
