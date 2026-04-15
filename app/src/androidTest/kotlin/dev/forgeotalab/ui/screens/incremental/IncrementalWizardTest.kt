package dev.forgeotalab.ui.screens.incremental

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.forgeotalab.ui.theme.ForgeOtaLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the Incremental Validation Wizard.
 *
 * Coverage:
 * - Blocked state → extraction CTA disabled
 * - Hash mismatch → field-level diff display
 * - Valid base → extraction CTA enabled
 * - Missing base → import CTA per partition
 *
 * PRD failure taxonomy rows 5-6:
 * - Row 5: Missing incremental base → blocked
 * - Row 6: Base image mismatch → field-level diff shown
 */
@RunWith(AndroidJUnit4::class)
class IncrementalWizardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blocked_state_disables_extraction_cta() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                IncrementalWizardScreen(
                    uiState = IncrementalWizardUiState.Blocked(
                        reason = "Missing base partition images for incremental OTA",
                        partitionsNeedingBase = listOf("system", "vendor"),
                    ),
                    onImportBaseClick = { _ -> },
                    onExtractClick = {},
                    onBackClick = {},
                )
            }
        }

        // Extract CTA should be disabled when blocked
        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertIsNotEnabled()

        // Blocked reason should be displayed
        composeTestRule
            .onNodeWithText("Missing base", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun mismatch_shows_field_level_diff() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                IncrementalWizardScreen(
                    uiState = IncrementalWizardUiState.Mismatch(
                        partitionName = "system",
                        expectedHash = "aabbccdd11223344...",
                        actualHash = "eeff00112233aabb...",
                        expectedSize = 2_147_483_648L,
                        actualSize = 2_147_483_648L,
                    ),
                    onImportBaseClick = { _ -> },
                    onExtractClick = {},
                    onBackClick = {},
                )
            }
        }

        // Field-level diff should be visible
        composeTestRule
            .onNodeWithText("aabbccdd", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("eeff0011", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun valid_base_enables_extraction() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                IncrementalWizardScreen(
                    uiState = IncrementalWizardUiState.Ready(
                        validatedPartitions = listOf("system", "vendor"),
                        totalPartitions = 2,
                    ),
                    onImportBaseClick = { _ -> },
                    onExtractClick = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Extract", ignoreCase = true)
            .assertIsEnabled()
    }

    @Test
    fun missing_base_shows_import_cta_per_partition() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                IncrementalWizardScreen(
                    uiState = IncrementalWizardUiState.Blocked(
                        reason = "Base images required",
                        partitionsNeedingBase = listOf("system", "vendor"),
                    ),
                    onImportBaseClick = { _ -> },
                    onExtractClick = {},
                    onBackClick = {},
                )
            }
        }

        // Each partition needing a base should have an import action
        composeTestRule.onNodeWithText("system", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("vendor", substring = true).assertIsDisplayed()
    }
}
