package dev.forgeotalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Prominent banner displayed at the top of the history list when
 * interrupted jobs are detected (FR-9).
 *
 * Accessibility:
 *   - Banner has contentDescription summarizing the interruption state
 *   - Resume and Clean up buttons have semantic labels
 *   - Progress indicator announces completed/total partitions
 *   - Touch targets ≥ 48dp on all interactive elements
 *
 * Visual treatment: Uses statusWarning background tint to draw attention
 * without conveying error — interruption is recoverable, not a failure.
 */
@Composable
fun ResumeBanner(
    packageDisplayName: String,
    completedPartitions: Int,
    totalPartitions: Int,
    onResume: () -> Unit,
    onCleanup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val progress = if (totalPartitions > 0) {
        completedPartitions.toFloat() / totalPartitions.toFloat()
    } else {
        0f
    }

    val accessibilityText = buildString {
        append("Extraction was interrupted for $packageDisplayName. ")
        append("$completedPartitions of $totalPartitions partitions completed. ")
        append("Resume to continue, or clean up to discard unfinished work.")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceDefault)
            .padding(16.dp)
            .semantics { contentDescription = accessibilityText },
    ) {
        // Header row with warning icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null, // Covered by parent semantics
                modifier = Modifier.size(20.dp),
                tint = colors.feedbackWarningIcon,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Extraction interrupted",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Package name
        Text(
            text = packageDisplayName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar and label
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = colors.actionPrimaryBg,
            trackColor = colors.surfaceRaised,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$completedPartitions of $totalPartitions partitions completed",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(
                onClick = onCleanup,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textSecondary,
                ),
            ) {
                Text("Clean up")
            }

            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionPrimaryBg,
                    contentColor = colors.textOnAction,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Resume")
            }
        }
    }
}
