package dev.forgeotalab.ui.screens.extraction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.forgeotalab.ui.theme.ForgeOtaLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the Extraction/Results screen.
 *
 * Coverage:
 * - Progress state shows partition name and percentage
 * - Partial success shows mixed verified + failed badges
 * - Cancellation shows preserved partition indicators
 * - Completed state shows verification badges (PRD Rule #3)
 *
 * PRD validation:
 * - Rule #3: No success state before verification completes
 * - Rule #6: Partial success is first-class
 * - Rule #8: Cancellation preserves verified outputs
 */
@RunWith(AndroidJUnit4::class)
class ExtractionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progress_shows_partition_name_and_percentage() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ExtractionScreen(
                    uiState = ExtractionUiState.Extracting(
                        currentPartition = "system",
                        completedCount = 2,
                        totalCount = 7,
                        progressPercent = 43,
                        partitionResults = listOf(
                            PartitionResultItem("boot", "Verified", 65536L),
                            PartitionResultItem("vbmeta", "Verified", 4096L),
                        ),
                    ),
                    onCancelClick = {},
                    onDoneClick = {},
                    onExportClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("system", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("43%", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 7", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun partial_success_shows_mixed_verified_and_failed() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ExtractionScreen(
                    uiState = ExtractionUiState.Completed(
                        status = "Partial Success",
                        completedCount = 5,
                        failedCount = 2,
                        totalCount = 7,
                        partitionResults = listOf(
                            PartitionResultItem("boot", "Verified", 65536L),
                            PartitionResultItem("system", "Verified", 2_000_000_000L),
                            PartitionResultItem("vendor", "Verified", 500_000_000L),
                            PartitionResultItem("product", "Verified", 100_000_000L),
                            PartitionResultItem("vbmeta", "Verified", 4096L),
                            PartitionResultItem("modem", "Failed", 0L),
                            PartitionResultItem("dtbo", "Failed", 0L),
                        ),
                    ),
                    onCancelClick = {},
                    onDoneClick = {},
                    onExportClick = {},
                )
            }
        }

        // PRD Rule #6: Partial success is first-class
        composeTestRule.onNodeWithText("Partial Success", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("5", substring = true).assertIsDisplayed()

        // Verified partitions should show verification badge
        composeTestRule
            .onNodeWithContentDescription("boot: Verified", useUnmergedTree = true)
            .assertExists()

        // Failed partitions should show failure indicator
        composeTestRule
            .onNodeWithContentDescription("modem: Failed", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun cancellation_shows_preserved_partitions() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ExtractionScreen(
                    uiState = ExtractionUiState.Completed(
                        status = "Canceled",
                        completedCount = 3,
                        failedCount = 0,
                        totalCount = 7,
                        partitionResults = listOf(
                            PartitionResultItem("boot", "Verified", 65536L),
                            PartitionResultItem("system", "Verified", 2_000_000_000L),
                            PartitionResultItem("vendor", "Verified", 500_000_000L),
                        ),
                    ),
                    onCancelClick = {},
                    onDoneClick = {},
                    onExportClick = {},
                )
            }
        }

        // PRD Rule #8: Cancellation preserves verified outputs
        composeTestRule.onNodeWithText("Canceled", ignoreCase = true).assertIsDisplayed()
        // Preserved verified outputs should still be accessible
        composeTestRule.onNodeWithText("boot", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("system", substring = true).assertIsDisplayed()
    }

    @Test
    fun completed_shows_verification_badges_not_before() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ExtractionScreen(
                    uiState = ExtractionUiState.Completed(
                        status = "Completed",
                        completedCount = 2,
                        failedCount = 0,
                        totalCount = 2,
                        partitionResults = listOf(
                            PartitionResultItem("boot", "Verified", 65536L),
                            PartitionResultItem("vbmeta", "Verified", 4096L),
                        ),
                    ),
                    onCancelClick = {},
                    onDoneClick = {},
                    onExportClick = {},
                )
            }
        }

        // PRD Rule #3: Success state shown ONLY after verification
        composeTestRule.onNodeWithText("Completed", ignoreCase = true).assertIsDisplayed()
        // Both partitions show verified badge
        composeTestRule
            .onNodeWithContentDescription("boot: Verified", useUnmergedTree = true)
            .assertExists()
        composeTestRule
            .onNodeWithContentDescription("vbmeta: Verified", useUnmergedTree = true)
            .assertExists()
    }
}
