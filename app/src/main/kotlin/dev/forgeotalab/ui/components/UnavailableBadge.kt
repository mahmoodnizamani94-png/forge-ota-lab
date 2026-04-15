package dev.forgeotalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Small badge indicating a history item's source URI is no longer accessible.
 *
 * Shown when the SAF persistable URI permission has been revoked, the
 * source file has been deleted, or external storage is disconnected.
 *
 * PRD FR-11: "If stored URI is no longer accessible: show Unavailable badge,
 * offer removal."
 *
 * Accessibility: The badge text is self-describing ("File unavailable")
 * and doesn't require a separate contentDescription.
 *
 * Visual treatment: Subdued error-surface background with dimmed text.
 * Not full error red — the item isn't failed, it's just inaccessible.
 */
@Composable
fun UnavailableBadge(
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Text(
        text = "File unavailable",
        style = MaterialTheme.typography.labelSmall,
        color = colors.feedbackErrorText,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.feedbackErrorSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
