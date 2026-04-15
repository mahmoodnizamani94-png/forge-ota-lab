package dev.forgeotalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Reusable support tier badge composable.
 *
 * WHY a shared component: The support tier badge appears on the home screen
 * (history cards), analysis screen (header), and result screen. Consistent
 * visual treatment — color, shape, and semantics — must be centralized.
 *
 * Color mapping per PRD UI/UX Architecture:
 * - Supported → success colors (green family)
 * - Experimental → warning colors (amber family)
 * - Forensic → info colors (teal family, never green)
 *
 * Accessibility: contentDescription announces the tier name for TalkBack.
 * Describes meaning ("Supported"), not appearance ("green badge").
 */
@Composable
fun SupportTierBadge(
    tier: SupportTier,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val (bgColor, textColor, label) = when (tier) {
        SupportTier.SUPPORTED -> Triple(
            colors.feedbackSuccessSurface,
            colors.feedbackSuccessText,
            "Supported",
        )
        SupportTier.EXPERIMENTAL -> Triple(
            colors.feedbackWarningSurface,
            colors.feedbackWarningText,
            "Experimental",
        )
        SupportTier.FORENSIC -> Triple(
            colors.feedbackInfoSurface,
            colors.feedbackInfoText,
            "Forensic",
        )
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics {
                contentDescription = "$label support tier"
            },
    )
}

/**
 * Variant that displays a classification label (Full OTA, Incremental, etc.)
 * alongside the tier badge.
 */
@Composable
fun ClassificationBadge(
    classification: String,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val displayLabel = when (classification) {
        "SUPPORTED_FULL" -> "Full OTA"
        "SUPPORTED_INCREMENTAL" -> "Incremental OTA"
        "EXPERIMENTAL" -> "Experimental"
        "FORENSIC" -> "Forensic"
        "IMAGE_ONLY" -> "Standalone Image"
        "CORRUPTED" -> "Corrupted"
        "UNKNOWN" -> "Unknown Format"
        else -> classification
    }

    Text(
        text = displayLabel,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
