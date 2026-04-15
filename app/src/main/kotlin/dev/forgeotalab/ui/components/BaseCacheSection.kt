package dev.forgeotalab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.forgeotalab.data.entity.BaseCacheEntity
import dev.forgeotalab.ui.theme.ForgeTheme
import java.util.Locale

/**
 * Reusable base image cache management section.
 *
 * Used in:
 * 1. IncrementalWizardScreen — compact cache summary with usage bar
 * 2. Settings screen (future) — full cache management with per-entry delete
 *
 * Accessibility:
 * - "Base image cache" heading for TalkBack
 * - Each cache entry has content description with partition + fingerprint
 * - Delete buttons have semantic labels
 * - Usage bar announced as fraction
 */
@Composable
fun BaseCacheSection(
    cacheEntries: List<BaseCacheEntity>,
    cacheUsedBytes: Long,
    cacheCeilingBytes: Long,
    cacheUsedFormatted: String,
    cacheCeilingFormatted: String,
    onDeleteEntry: (String) -> Unit,
    onClearAll: () -> Unit,
    onCeilingChanged: (Long) -> Unit,
    showEntries: Boolean = true,
    showCeilingSlider: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val usagePercent = if (cacheCeilingBytes > 0) {
        (cacheUsedBytes.toFloat() / cacheCeilingBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Base Image Cache",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (cacheEntries.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.semantics {
                            contentDescription = "Clear all cached base images"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Usage summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${cacheEntries.size} cached images",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = "$cacheUsedFormatted / $cacheCeilingFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Usage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceRaised),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usagePercent)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                usagePercent > 0.9f -> colors.feedbackErrorIcon
                                usagePercent > 0.7f -> colors.feedbackWarningIcon
                                else -> colors.actionPrimaryBg
                            },
                        ),
                )
            }

            // Ceiling slider (settings mode)
            AnimatedVisibility(
                visible = showCeilingSlider,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                CeilingSlider(
                    currentBytes = cacheCeilingBytes,
                    onCeilingChanged = onCeilingChanged,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // Per-entry list (expandable)
            AnimatedVisibility(
                visible = showEntries && cacheEntries.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cacheEntries.forEach { entry ->
                        CacheEntryRow(
                            entry = entry,
                            onDelete = { onDeleteEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Cache entry row
// =============================================================================

@Composable
private fun CacheEntryRow(
    entry: BaseCacheEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "${entry.partitionName} base image, fingerprint ${entry.fingerprint.take(40)}"
            },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.partitionName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = entry.fingerprint.take(50) + if (entry.fingerprint.length > 50) "…" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete cached ${entry.partitionName} base image",
                tint = colors.feedbackErrorIcon,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// =============================================================================
// Ceiling slider
// =============================================================================

@Composable
private fun CeilingSlider(
    currentBytes: Long,
    onCeilingChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    // Convert to GB for slider display
    val minGb = 0.1f
    val maxGb = 10f
    var sliderValue by remember(currentBytes) {
        mutableFloatStateOf((currentBytes.toFloat() / (1024 * 1024 * 1024)).coerceIn(minGb, maxGb))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cache ceiling",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = String.format(Locale.US, "%.1f GB", sliderValue),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onCeilingChanged((sliderValue * 1024 * 1024 * 1024).toLong())
            },
            valueRange = minGb..maxGb,
            steps = 19, // 0.5 GB increments
            colors = SliderDefaults.colors(
                thumbColor = colors.actionPrimaryBg,
                activeTrackColor = colors.actionPrimaryBg,
                inactiveTrackColor = colors.surfaceRaised,
            ),
        )
    }
}

// =============================================================================
// Formatting
// =============================================================================

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1024} KB"
        bytes < 1_073_741_824 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    }
}
