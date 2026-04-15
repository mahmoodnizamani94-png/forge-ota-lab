@file:OptIn(ExperimentalLayoutApi::class)

package dev.forgeotalab.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Theme preview gallery — renders every visual token for design verification.
 *
 * Verifies:
 *   1. Surface swatches match obsidian/near-white hierarchy
 *   2. Text hierarchy maintains contrast on all surface levels
 *   3. Button states (primary, secondary, ghost, destructive)
 *   4. Card styling with subtle borders
 *   5. Badge variants (brand, success, warning)
 *   6. Status indicators (verified, failed, skipped, pending)
 *   7. Progress bar fills (default, success, error)
 *   8. Alert banners (error, warning, success, info)
 *   9. Brand copper vs error red vs warning amber — visual distinctness
 *
 * Open this file in Android Studio → Preview pane for dual-mode verification.
 */

// =============================================================================
// Preview entry points — one dark, one light
// =============================================================================

@Preview(
    name = "Forge Theme Gallery — Dark",
    showBackground = true,
    backgroundColor = 0xFF1A1816,
    widthDp = 400,
    heightDp = 2400,
)
@Composable
private fun ThemePreviewDark() {
    ForgeTheme(useDarkTheme = true) {
        ThemeGalleryContent()
    }
}

@Preview(
    name = "Forge Theme Gallery — Light",
    showBackground = true,
    backgroundColor = 0xFFF8F5F2,
    widthDp = 400,
    heightDp = 2400,
)
@Composable
private fun ThemePreviewLight() {
    ForgeTheme(useDarkTheme = false) {
        ThemeGalleryContent()
    }
}

// =============================================================================
// Gallery content
// =============================================================================

@Composable
private fun ThemeGalleryContent() {
    val colors = ForgeTheme.colors
    val components = ForgeTheme.components
    val modeLabel = if (colors.isDark) "DARK" else "LIGHT"

    Column(
        modifier = Modifier
            .background(colors.surfacePage)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Header ──
        Text(
            text = "Forge Theme Gallery — $modeLabel",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )

        // ── 1. Surface Swatches ──
        SectionTitle("Surfaces")
        SurfaceSwatchRow(colors)

        // ── 2. Text Hierarchy ──
        SectionTitle("Text Hierarchy")
        TextHierarchySection(colors)

        // ── 3. Buttons ──
        SectionTitle("Buttons")
        ButtonsSection(colors, components)

        // ── 4. Cards ──
        SectionTitle("Cards")
        CardsSection(colors, components)

        // ── 5. Badges ──
        SectionTitle("Badges")
        BadgesSection(colors, components)

        // ── 6. Status Indicators ──
        SectionTitle("Status Indicators")
        StatusSection(components)

        // ── 7. Progress Bars ──
        SectionTitle("Progress Bars")
        ProgressSection(colors, components)

        // ── 8. Alert Banners ──
        SectionTitle("Alert Banners")
        AlertsSection(components)

        // ── 9. Brand vs Error vs Warning — Color Distinction ──
        SectionTitle("Color Distinction Check")
        ColorDistinctionSection()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// =============================================================================
// Section components
// =============================================================================

@Composable
private fun SectionTitle(title: String) {
    val colors = ForgeTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SurfaceSwatchRow(colors: ForgeColors) {
    val swatches = listOf(
        "Page" to colors.surfacePage,
        "Default" to colors.surfaceDefault,
        "Raised" to colors.surfaceRaised,
        "Overlay" to colors.surfaceOverlay,
        "Sunken" to colors.surfaceSunken,
        "Emphasis" to colors.surfaceEmphasis,
        "Inverse" to colors.surfaceInverse,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        swatches.forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp)),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun TextHierarchySection(colors: ForgeColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceDefault)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Primary text on default surface", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        Text("Secondary text — metadata and supporting content", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text("Tertiary text — hints and captions", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        Text("Disabled text — non-interactive", style = MaterialTheme.typography.bodySmall, color = colors.textDisabled)
        Text("Link text — navigation and actions", style = MaterialTheme.typography.bodyMedium, color = colors.textLink)
        Text(
            text = "Code text — monospace inline",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = colors.textCode,
        )
    }
}

@Composable
private fun ButtonsSection(colors: ForgeColors, components: ForgeComponentColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Primary
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = components.button.primaryBg,
                    contentColor = components.button.primaryText,
                ),
            ) { Text("Primary") }
            Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = components.button.primaryBgDisabled,
                    disabledContentColor = components.button.primaryTextDisabled,
                ),
            ) { Text("Disabled") }
        }

        // Secondary (outlined)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {},
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = components.button.secondaryText,
                ),
            ) { Text("Secondary") }
        }

        // Ghost
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {},
                colors = ButtonDefaults.textButtonColors(
                    contentColor = components.button.ghostText,
                ),
            ) { Text("Ghost") }
        }

        // Destructive
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = components.button.destructiveBg,
                    contentColor = components.button.destructiveText,
                ),
            ) { Text("Destructive") }
        }
    }
}

@Composable
private fun CardsSection(colors: ForgeColors, components: ForgeComponentColors) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = components.card.bg,
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(components.card.border),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Card Title",
                style = MaterialTheme.typography.titleMedium,
                color = components.card.textTitle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Card body text with secondary treatment. This is metadata or supporting description content.",
                style = MaterialTheme.typography.bodyMedium,
                color = components.card.textBody,
            )
        }
    }
}

@Composable
private fun BadgesSection(colors: ForgeColors, components: ForgeComponentColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeChip("Brand", components.badge.brandBg, components.badge.brandText)
        BadgeChip("Success", components.badge.successBg, components.badge.successText)
        BadgeChip("Warning", components.badge.warningBg, components.badge.warningText)
    }
}

@Composable
private fun BadgeChip(label: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

@Composable
private fun StatusSection(components: ForgeComponentColors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow(
            icon = Icons.Filled.CheckCircle,
            iconColor = components.status.verifiedIcon,
            text = "Verified — SHA-256 match confirmed",
            textColor = components.status.verifiedText,
        )
        StatusRow(
            icon = Icons.Filled.Error,
            iconColor = components.status.failedIcon,
            text = "Failed — hash mismatch detected",
            textColor = components.status.failedText,
        )
        StatusRow(
            icon = Icons.Filled.RemoveCircle,
            iconColor = components.status.skippedIcon,
            text = "Skipped — partition excluded",
            textColor = components.status.skippedText,
        )
        StatusRow(
            icon = Icons.Filled.HourglassEmpty,
            iconColor = components.status.pendingIcon,
            text = "Pending — awaiting extraction",
            textColor = ForgeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    text: String,
    textColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative in gallery
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

@Composable
private fun ProgressSection(colors: ForgeColors, components: ForgeComponentColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Default progress
        Column {
            Text("Default (65%)", style = MaterialTheme.typography.labelSmall, color = components.progress.text)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0.65f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = components.progress.fill,
                trackColor = components.progress.trackBg,
            )
        }

        // Success progress
        Column {
            Text("Complete (100%)", style = MaterialTheme.typography.labelSmall, color = components.progress.text)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = components.progress.fillSuccess,
                trackColor = components.progress.trackBg,
            )
        }

        // Error progress
        Column {
            Text("Failed (40%)", style = MaterialTheme.typography.labelSmall, color = components.progress.text)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0.40f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = components.progress.fillError,
                trackColor = components.progress.trackBg,
            )
        }
    }
}

@Composable
private fun AlertsSection(components: ForgeComponentColors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertBanner(
            icon = Icons.Filled.Error,
            iconColor = components.alert.errorIcon,
            text = "Verification failed: SHA-256 mismatch on system.img",
            textColor = components.alert.errorText,
            bg = components.alert.errorBg,
            borderColor = components.alert.errorBorder,
        )
        AlertBanner(
            icon = Icons.Filled.Warning,
            iconColor = components.alert.warningIcon,
            text = "Low storage: 210 MB available, 340 MB needed",
            textColor = components.alert.warningText,
            bg = components.alert.warningBg,
            borderColor = components.alert.warningBorder,
        )
        AlertBanner(
            icon = Icons.Filled.CheckCircle,
            iconColor = components.alert.successIcon,
            text = "All 7 partitions verified successfully",
            textColor = components.alert.successText,
            bg = components.alert.successBg,
            borderColor = components.alert.successBorder,
        )
        AlertBanner(
            icon = Icons.Filled.Info,
            iconColor = components.alert.infoIcon,
            text = "Forensic mode: unknown format, read-only analysis",
            textColor = components.alert.infoText,
            bg = components.alert.infoBg,
            borderColor = components.alert.infoBorder,
        )
    }
}

@Composable
private fun AlertBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    text: String,
    textColor: Color,
    bg: Color,
    borderColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
        )
    }
}

/**
 * Brand copper (H=38°) next to error red (H=22°) next to warning amber (H=70°).
 *
 * This section proves visual separation at a glance:
 *   - ΔH between brand and error = 16° (copper is warmer/oranger than error red)
 *   - ΔH between brand and warning = 32° (copper is cooler/redder than warning amber)
 *   - All three have different chroma anchors ensuring distinct saturation
 */
@Composable
private fun ColorDistinctionSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorSwatch("Brand\nH=38°", ForgePalette.Brand500, Modifier.weight(1f))
        ColorSwatch("Error\nH=22°", ForgePalette.Error500, Modifier.weight(1f))
        ColorSwatch("Warning\nH=70°", ForgePalette.Warning500, Modifier.weight(1f))
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ForgeTheme.colors.textSecondary,
        )
    }
}
