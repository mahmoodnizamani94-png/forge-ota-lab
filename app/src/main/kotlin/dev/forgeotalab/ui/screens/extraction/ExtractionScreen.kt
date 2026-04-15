package dev.forgeotalab.ui.screens.extraction

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Extraction screen — shows preflight, progress, and results for an extraction job.
 *
 * Accessibility:
 *   - Screen title "Extraction Progress" / "Extraction Complete" for TalkBack
 *   - Progress updates announced every 10% via LiveRegion.Polite
 *   - Cancel button: "Cancel extraction. Verified partitions will be preserved."
 *   - Status icons describe meaning ("Verified"), not appearance ("green circle")
 *   - Touch targets ≥ 48dp on all interactive elements
 *
 * States (PRD State Matrix, Extraction row):
 *   - Loading: "Preparing extraction…" spinner
 *   - WaitingForDirectory: SAF directory picker prompt
 *   - PreflightFailed: Specific error with actionable CTA
 *   - Running: Progress bar with partition list
 *   - Completed: All verified with artifact list
 *   - PartialSuccess: Mixed results (verified + failed)
 *   - Failed: All failed with diagnostics
 *   - Canceled: Preserved outputs shown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionScreen(
    packageId: String,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit = {},
    viewModel: ExtractionViewModel = hiltViewModel(),
) {
    val colors = ForgeTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(packageId) {
        viewModel.initialize(packageId)
    }

    // Auto-navigate to ResultScreen when extraction completes.
    // WHY LaunchedEffect(Unit): SharedFlow has replay=0, so collecting
    // once is sufficient — it won't re-fire on recomposition.
    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect { jobId ->
            onNavigateToResult(jobId)
        }
    }

    val screenTitle = when (uiState) {
        is ExtractionUiState.Running -> "Extracting…"
        is ExtractionUiState.Completed -> "Extraction Complete"
        is ExtractionUiState.PartialSuccess -> "Partial Success"
        is ExtractionUiState.Failed -> "Extraction Failed"
        is ExtractionUiState.Canceled -> "Extraction Canceled"
        else -> "Extraction"
    }

    Scaffold(
        containerColor = colors.surfacePage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfacePage,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textSecondary,
                ),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            label = "extraction_state",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { state ->
            when (state) {
                is ExtractionUiState.Loading -> {
                    LoadingContent(
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.WaitingForDirectory -> {
                    WaitingForDirectoryContent(
                        onSelectDirectory = {
                            // TODO(P06): Wire SAF directory picker via ActivityResultContract
                            // For now, use app's private cache directory
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.PreflightFailed -> {
                    PreflightFailedContent(
                        message = state.message,
                        actionLabel = state.actionLabel,
                        onAction = onNavigateBack,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.Running -> {
                    RunningContent(
                        data = state.data,
                        onCancel = viewModel::cancelExtraction,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.Completed -> {
                    ResultContent(
                        data = state.data,
                        statusTitle = "All Partitions Verified",
                        statusIcon = Icons.Outlined.Verified,
                        statusColor = colors.feedbackSuccessIcon,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.PartialSuccess -> {
                    ResultContent(
                        data = state.data,
                        statusTitle = "Partial Success",
                        statusIcon = Icons.Outlined.Warning,
                        statusColor = colors.feedbackWarningIcon,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.Failed -> {
                    ResultContent(
                        data = state.data,
                        statusTitle = "Extraction Failed",
                        statusIcon = Icons.Outlined.Error,
                        statusColor = colors.feedbackErrorIcon,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.Canceled -> {
                    ResultContent(
                        data = state.data,
                        statusTitle = "Extraction Canceled",
                        statusIcon = Icons.Outlined.Cancel,
                        statusColor = colors.textTertiary,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ExtractionUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

// =============================================================================
// Loading state
// =============================================================================

@Composable
private fun LoadingContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = colors.actionPrimaryBg,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
    }
}

// =============================================================================
// Waiting for directory
// =============================================================================

@Composable
private fun WaitingForDirectoryContent(
    onSelectDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null, // Decorative — "Choose Output Directory" text conveys meaning
                modifier = Modifier.size(64.dp),
                tint = colors.actionPrimaryBg,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Choose Output Directory",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select where extracted partition images will be saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSelectDirectory,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionPrimaryBg,
                    contentColor = colors.textOnAction,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null, // Decorative — "Select Directory" text conveys meaning
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select Directory",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// =============================================================================
// Preflight failed
// =============================================================================

@Composable
private fun PreflightFailedContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceDefault,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Preflight check failed",
                    modifier = Modifier.size(48.dp),
                    tint = colors.feedbackWarningIcon,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cannot Start Extraction",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.actionPrimaryBg,
                        contentColor = colors.textOnAction,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Running — active extraction progress
// =============================================================================

@Composable
private fun RunningContent(
    data: ExtractionProgressData,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Overall progress card
        item(key = "progress_header") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaceDefault,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    // Status text with live region for accessibility
                    Text(
                        text = "Extracting ${data.currentPartition}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "Extracting ${data.currentPartition}, " +
                                "${data.completedPartitions} of ${data.totalPartitions} " +
                                "partitions complete, ${data.overallPercent} percent"
                        },
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${data.completedPartitions}/${data.totalPartitions} partitions · ${data.overallPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { data.overallPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.actionPrimaryBg,
                        trackColor = colors.surfaceSunken,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Elapsed / ETA row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Elapsed: ${data.elapsedTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                        data.estimatedTimeRemaining?.let { eta ->
                            Text(
                                text = "~$eta remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }

                    // Battery warning
                    data.batteryWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = colors.feedbackWarningSurface,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = "Battery warning",
                                modifier = Modifier.size(16.dp),
                                tint = colors.feedbackWarningIcon,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.feedbackWarningText,
                            )
                        }
                    }
                }
            }
        }

        // Partition status list
        item(key = "partitions_header") {
            Text(
                text = "Partitions",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() },
            )
        }

        items(
            items = data.partitionStatuses,
            key = { it.partitionId },
        ) { item ->
            PartitionStatusRow(item = item)
        }

        // Cancel button
        item(key = "cancel_button") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.feedbackErrorText,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null, // Decorative — "Cancel Extraction" text conveys meaning
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cancel Extraction",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "Cancel extraction. Verified partitions will be preserved."
                    },
                )
            }
        }
    }
}

// =============================================================================
// Partition status row
// =============================================================================

@Composable
private fun PartitionStatusRow(
    item: PartitionProgressItem,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val (icon, iconTint, statusText) = when (item.status) {
        "COMPLETED" -> Triple(
            Icons.Outlined.CheckCircle,
            colors.feedbackSuccessIcon,
            when (item.verificationStatus) {
                "VERIFIED" -> "Verified"
                "UNVERIFIABLE" -> "Extracted (unverifiable)"
                "MISMATCH" -> "Hash mismatch"
                else -> "Complete"
            },
        )
        "RUNNING" -> Triple(
            Icons.Outlined.HourglassEmpty,
            colors.actionPrimaryBg,
            "Extracting…",
        )
        "FAILED" -> Triple(
            Icons.Outlined.Error,
            colors.feedbackErrorIcon,
            "Failed",
        )
        else -> Triple(
            Icons.Outlined.HourglassEmpty,
            colors.textDisabled,
            "Pending",
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = statusText,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.partitionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = iconTint,
                )
            }

            // Progress for running items
            if (item.status == "RUNNING" && item.percent > 0) {
                Text(
                    text = "${item.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

// =============================================================================
// Result content — Completed, PartialSuccess, Failed, Canceled
// =============================================================================

@Composable
private fun ResultContent(
    data: ExtractionResultData,
    statusTitle: String,
    statusIcon: androidx.compose.ui.graphics.vector.ImageVector,
    statusColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Result summary card
        item(key = "result_header") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaceDefault,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusTitle,
                        modifier = Modifier.size(56.dp),
                        tint = statusColor,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatBadge(
                            label = "Verified",
                            count = data.verifiedCount,
                            color = colors.feedbackSuccessIcon,
                        )
                        if (data.unverifiableCount > 0) {
                            StatBadge(
                                label = "Unverifiable",
                                count = data.unverifiableCount,
                                color = colors.textTertiary,
                            )
                        }
                        if (data.mismatchCount > 0) {
                            StatBadge(
                                label = "Mismatch",
                                count = data.mismatchCount,
                                color = colors.feedbackWarningIcon,
                            )
                        }
                        if (data.failedCount > 0) {
                            StatBadge(
                                label = "Failed",
                                count = data.failedCount,
                                color = colors.feedbackErrorIcon,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Duration: ${data.durationFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        // Artifact list
        if (data.artifacts.isNotEmpty()) {
            item(key = "artifacts_header") {
                Text(
                    text = "Extracted Artifacts",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }

            items(
                items = data.artifacts,
                key = { it.id },
            ) { artifact ->
                ArtifactCard(artifact = artifact)
            }
        }
    }
}

// =============================================================================
// Stat badge
// =============================================================================

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ForgeTheme.colors.textTertiary,
        )
    }
}

// =============================================================================
// Artifact card
// =============================================================================

@Composable
private fun ArtifactCard(
    artifact: ArtifactDisplayItem,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val verificationIcon = when (artifact.verificationStatus) {
        "VERIFIED" -> Icons.Outlined.Verified
        "MISMATCH" -> Icons.Outlined.Warning
        "UNVERIFIABLE" -> Icons.Outlined.HourglassEmpty
        else -> Icons.Outlined.HourglassEmpty
    }

    val verificationColor = when (artifact.verificationStatus) {
        "VERIFIED" -> colors.feedbackSuccessIcon
        "MISMATCH" -> colors.feedbackWarningIcon
        "UNVERIFIABLE" -> colors.textTertiary
        else -> colors.textDisabled
    }

    val verificationLabel = when (artifact.verificationStatus) {
        "VERIFIED" -> "SHA-256 verified"
        "MISMATCH" -> "Hash mismatch — suspect output"
        "UNVERIFIABLE" -> "No target hash available"
        else -> "Pending verification"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Verification icon
                Icon(
                    imageVector = verificationIcon,
                    contentDescription = verificationLabel,
                    modifier = Modifier.size(24.dp),
                    tint = verificationColor,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${artifact.partitionName}.img",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = artifact.sizeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                        Text(
                            text = artifact.derivationType.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }

            // Verification status
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = verificationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = verificationColor,
            )

            // SHA-256 hash (truncated)
            if (artifact.sha256.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SHA-256: ${artifact.sha256.take(16)}…",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// =============================================================================
// Error content
// =============================================================================

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = "Extraction failed",
                modifier = Modifier.size(48.dp),
                tint = colors.feedbackErrorIcon,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
