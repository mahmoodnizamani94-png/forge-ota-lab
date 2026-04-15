package dev.forgeotalab.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.forgeotalab.ui.theme.ForgeOtaLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI test for concurrent job rejection (PRD Rule #7).
 *
 * PRD: "Only one extraction job active at a time. Analysis can proceed in parallel."
 *
 * Coverage:
 * - Second extraction blocked while first is running → user sees message
 * - Analysis proceeds during active extraction → no block
 */
@RunWith(AndroidJUnit4::class)
class ConcurrentJobRejectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun second_extraction_blocked_while_first_running() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ConcurrentJobBlock(
                    isExtractionActive = true,
                    activeJobName = "pixel_ota.zip",
                )
            }
        }

        // Should show a message that another job is active
        composeTestRule
            .onNodeWithText("extraction", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("pixel_ota.zip", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun analysis_proceeds_during_active_extraction() {
        composeTestRule.setContent {
            ForgeOtaLabTheme {
                ConcurrentJobBlock(
                    isExtractionActive = true,
                    activeJobName = "pixel_ota.zip",
                    allowAnalysis = true,
                )
            }
        }

        // Analysis should not be blocked
        composeTestRule
            .onNodeWithText("Analysis available", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}
