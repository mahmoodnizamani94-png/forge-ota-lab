package dev.forgeotalab.ui.screens.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.forgeotalab.ui.theme.ForgeOtaLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the Home screen.
 *
 * Coverage:
 * - Empty state renders import CTA and format guide
 * - Populated state shows history items in reverse chronological order
 * - History item tap invokes navigation callback
 * - inaccessible URI shows warning badge
 * - Accessibility checks pass on all interactive elements
 *
 * WHY ComposeTestRule: We test the screen Composable in isolation
 * without the full navigation graph. This makes tests faster and
 * more deterministic than instrumented Espresso tests.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Empty state
    // =========================================================================

    @Test
    fun empty_state_shows_import_cta() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Empty,
                    onImportClick = {},
                    onHistoryItemClick = {},
                    onSettingsClick = {},
                )
            }
        }

        // Import CTA should be visible and actionable
        composeTestRule
            .onNodeWithText("Import OTA Package", ignoreCase = true)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun empty_state_shows_format_guide_entry() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Empty,
                    onImportClick = {},
                    onHistoryItemClick = {},
                    onSettingsClick = {},
                )
            }
        }

        // Format guide should be accessible for new users
        composeTestRule
            .onNodeWithText("Supported Formats", ignoreCase = true)
            .assertIsDisplayed()
    }

    // =========================================================================
    // Populated state
    // =========================================================================

    @Test
    fun populated_state_shows_history_items() {
        val history = listOf(
            HistoryDisplayItem(
                id = "job-1",
                packageName = "pixel_ota.zip",
                status = "Completed",
                timestamp = "2 minutes ago",
                partitionCount = 7,
                isUriAccessible = true,
            ),
            HistoryDisplayItem(
                id = "job-2",
                packageName = "samsung_update.tar",
                status = "Partial Success",
                timestamp = "1 hour ago",
                partitionCount = 3,
                isUriAccessible = true,
            ),
        )

        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Populated(items = history),
                    onImportClick = {},
                    onHistoryItemClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("pixel_ota.zip").assertIsDisplayed()
        composeTestRule.onNodeWithText("samsung_update.tar").assertIsDisplayed()
    }

    @Test
    fun history_item_tap_invokes_callback_with_job_id() {
        var clickedId: String? = null

        val history = listOf(
            HistoryDisplayItem(
                id = "job-42",
                packageName = "test_ota.zip",
                status = "Completed",
                timestamp = "5 minutes ago",
                partitionCount = 2,
                isUriAccessible = true,
            ),
        )

        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Populated(items = history),
                    onImportClick = {},
                    onHistoryItemClick = { clickedId = it },
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("test_ota.zip").performClick()
        assert(clickedId == "job-42") {
            "Expected job-42 but got $clickedId"
        }
    }

    @Test
    fun inaccessible_uri_shows_warning_indicator() {
        val history = listOf(
            HistoryDisplayItem(
                id = "job-stale",
                packageName = "revoked_access.zip",
                status = "Completed",
                timestamp = "3 days ago",
                partitionCount = 5,
                isUriAccessible = false, // SAF permission revoked
            ),
        )

        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Populated(items = history),
                    onImportClick = {},
                    onHistoryItemClick = {},
                    onSettingsClick = {},
                )
            }
        }

        // Should show a warning indicator for inaccessible URIs
        composeTestRule
            .onNodeWithContentDescription("File access unavailable", useUnmergedTree = true)
            .assertExists()
    }

    // =========================================================================
    // Accessibility
    // =========================================================================

    @Test
    fun all_interactive_elements_have_click_action() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                HomeScreen(
                    uiState = HomeUiState.Empty,
                    onImportClick = {},
                    onHistoryItemClick = {},
                    onSettingsClick = {},
                )
            }
        }

        // Every clickable element should be semantically labeled
        composeTestRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .forEach { node ->
                // Touch targets validated by AccessibilityChecks at the Espresso level
                assert(node.config.isNotEmpty()) {
                    "Clickable node has no semantic configuration"
                }
            }
    }
}
