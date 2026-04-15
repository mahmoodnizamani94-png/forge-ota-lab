package dev.forgeotalab.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.ui.theme.ForgeTheme
import kotlinx.coroutines.launch

/**
 * Three-screen onboarding flow — first launch only.
 *
 * Screen 1: Welcome — Forge wordmark + tagline + what this app does
 * Screen 2: Transparency — what this app doesn't do (explicit non-goals)
 * Screen 3: Privacy — telemetry consent toggle (default OFF) + "Get started"
 *
 * PRD: "Skippable. Never re-shown. Persisted via DataStore."
 *
 * Accessibility:
 *   - Each page title announced with heading semantics
 *   - Page indicators have contentDescription
 *   - Skip and Get Started buttons are descriptive
 *   - Consent toggle has descriptive label for TalkBack
 *   - Touch targets ≥ 48dp
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val colors = ForgeTheme.colors
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val telemetryConsent by viewModel.telemetryConsent.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage),
    ) {
        // Skip button — top right, visible on every page
        TextButton(
            onClick = { viewModel.skipOnboarding(onOnboardingComplete) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text(
                text = "Skip",
                color = colors.textSecondary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> TransparencyPage()
                    2 -> PrivacyPage(
                        telemetryConsent = telemetryConsent,
                        onConsentChanged = viewModel::setTelemetryConsent,
                    )
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Page ${pagerState.currentPage + 1} of 3"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) colors.actionPrimaryBg
                                else colors.borderSubtle,
                            ),
                    )
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                    ) {
                        Text("Back", color = colors.textSecondary)
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                if (pagerState.currentPage < 2) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionPrimaryBg,
                            contentColor = colors.textOnAction,
                        ),
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = { viewModel.completeOnboarding(onOnboardingComplete) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionPrimaryBg,
                            contentColor = colors.textOnAction,
                        ),
                    ) {
                        Text("Get started")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Onboarding pages
// =============================================================================

@Composable
private fun WelcomePage() {
    val colors = ForgeTheme.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Build,
            contentDescription = null, // Decorative hero — "Forge OTA Lab" heading follows
            modifier = Modifier.size(64.dp),
            tint = colors.actionPrimaryBg,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Forge OTA Lab",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mobile extraction workbench for Android OTA packages",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        BulletPoint("Import OTA packages, classify them truthfully by support tier")
        BulletPoint("Extract verified partition images — entirely on-device")
        BulletPoint("Know exactly what you're getting: direct, reconstructed, or partial")
    }
}

@Composable
private fun TransparencyPage() {
    val colors = ForgeTheme.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.NotInterested,
            contentDescription = null, // Decorative hero — "What this app doesn't do" heading follows
            modifier = Modifier.size(64.dp),
            tint = colors.feedbackWarningIcon,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "What this app doesn't do",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        NonGoalItem("Does not flash firmware to your device")
        NonGoalItem("Does not unlock bootloaders or provide root")
        NonGoalItem("Does not patch boot images or integrate Magisk")
        NonGoalItem("Does not download OEM firmware from the internet")
        NonGoalItem("Does not claim universal support for all formats")

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Forge analyzes and extracts. Honestly.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrivacyPage(
    telemetryConsent: Boolean,
    onConsentChanged: (Boolean) -> Unit,
) {
    val colors = ForgeTheme.colors

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PrivacyTip,
            contentDescription = null, // Decorative hero — "Your privacy matters" heading follows
            modifier = Modifier.size(64.dp),
            tint = colors.feedbackInfoIcon,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your privacy matters",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "All extraction happens locally on your device. " +
                "No package contents or filenames are ever uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Telemetry consent toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anonymous telemetry",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Help improve Forge by sharing anonymous crash reports and usage statistics",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = telemetryConsent,
                onCheckedChange = onConsentChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.actionPrimaryBg,
                    checkedTrackColor = colors.actionPrimaryBg.copy(alpha = 0.3f),
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.surfaceDefault,
                ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can change this anytime in Settings",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BulletPoint(text: String) {
    val colors = ForgeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.actionPrimaryBg,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun NonGoalItem(text: String) {
    val colors = ForgeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "✕",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.feedbackErrorIcon,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
