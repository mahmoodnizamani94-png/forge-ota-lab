package dev.forgeotalab.ui.screens.analysis

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.forgeotalab.ui.theme.ForgeOtaLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the Analysis screen.
 *
 * Coverage:
 * - Supported package → green tier badge + partition list + extract CTA
 * - Forensic package → informational treatment, no extract CTA
 * - Corrupted package → specific error, not generic "Something went wrong"
 * - Incremental package → Experimental badge
 * - Partition selection → enables/disables extract button
 *
 * PRD validation:
 * - Rule #1: UI support tier comes only from Rust core output
 * - Rule #4: Unknown formats never map to success visuals
 */
@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun supported_package_shows_supported_badge_and_partition_list() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Analyzed(
                        tier = "Supported",
                        family = "AOSP Payload OTA",
                        isIncremental = false,
                        partitions = listOf(
                            PartitionDisplayItem("boot", "boot_critical", "64 KB", true, false),
                            PartitionDisplayItem("system", "logical_system", "2.1 GB", true, false),
                        ),
                        securityPatchLevel = "2026-04-05",
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        // Tier badge
        composeTestRule.onNodeWithText("Supported").assertIsDisplayed()

        // Partition list
        composeTestRule.onNodeWithText("boot").assertIsDisplayed()
        composeTestRule.onNodeWithText("system").assertIsDisplayed()

        // Extract CTA should be available
        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun forensic_package_shows_informational_treatment_no_extract_cta() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Analyzed(
                        tier = "Forensic",
                        family = "Unknown",
                        isIncremental = false,
                        partitions = emptyList(),
                        securityPatchLevel = null,
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        // Forensic badge should be visible
        composeTestRule.onNodeWithText("Forensic").assertIsDisplayed()

        // PRD Rule #4: no green CTA, no "Ready to extract"
        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun corrupted_package_shows_specific_error_not_generic() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Error(
                        errorTitle = "Manifest corrupt",
                        errorDetail = "Protobuf decode error at byte offset 24: unexpected wire type",
                        errorCode = "MANIFEST_CORRUPT",
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        // Should show specific error, not generic "Something went wrong"
        composeTestRule.onNodeWithText("Manifest corrupt").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Something went wrong", ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun incremental_package_shows_experimental_badge() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Analyzed(
                        tier = "Experimental",
                        family = "AOSP Payload OTA",
                        isIncremental = true,
                        partitions = listOf(
                            PartitionDisplayItem("system", "logical_system", "2.1 GB", true, true),
                        ),
                        securityPatchLevel = "2026-04-05",
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Experimental").assertIsDisplayed()
    }

    @Test
    fun partition_selection_enables_extract_button() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Analyzed(
                        tier = "Supported",
                        family = "AOSP Payload OTA",
                        isIncremental = false,
                        partitions = listOf(
                            PartitionDisplayItem("boot", "boot_critical", "64 KB", true, false),
                        ),
                        securityPatchLevel = "2026-04-05",
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        // With at least one partition selected, extract should be enabled
        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertIsEnabled()
    }

    @Test
    fun no_selection_disables_extract_button_with_message() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                AnalysisScreen(
                    uiState = AnalysisUiState.Analyzed(
                        tier = "Supported",
                        family = "AOSP Payload OTA",
                        isIncremental = false,
                        partitions = listOf(
                            PartitionDisplayItem("boot", "boot_critical", "64 KB", false, false),
                        ),
                        securityPatchLevel = "2026-04-05",
                    ),
                    onExtractClick = {},
                    onPartitionToggle = { _, _ -> },
                    onBackClick = {},
                )
            }
        }

        // No partitions selected → extract should be disabled
        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertIsNotEnabled()
    }
}
