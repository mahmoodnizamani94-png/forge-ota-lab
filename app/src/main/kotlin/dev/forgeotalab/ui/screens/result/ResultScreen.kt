package dev.forgeotalab.ui.screens.result

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.contracts.model.JobStatus
import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Extraction results screen — the "verification before victory" principle as UI.
 *
 * This is where every extracted artifact's provenance and integrity becomes
 * crystal clear. A security researcher at Google Project Zero should look at
 * this screen and know immediately: was it verified? Against what hash? From
 * what source? Is it safe to flash?
 *
 * Accessibility:
 *   - Screen title "Extraction Results" with Role.Heading
 *   - Status badges describe meaning ("Verified"), not appearance ("green circle")
 *   - SHA-256 hashes readable by TalkBack (monospace, selectable)
 *   - Summary header uses LiveRegion for screen reader announcement
 *   - Touch targets ≥ 48dp on all interactive elements
 *   - Focus order: summary → artifact list → actions
 *
 * Process death recovery: All state loads from Room via jobId.
 * No in-memory-only state required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    jobId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFilesystemBrowser: (artifactId: String) -> Unit = {},
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val colors = ForgeTheme.colors
    val components = ForgeTheme.components
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(jobId) {
        viewModel.initialize(jobId)
    }

    // Handle one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultEvent.LaunchShareIntent -> {
                    val chooser = Intent.createChooser(event.intent, "Share artifact")
                    context.startActivity(chooser)
                }
                is ResultEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        containerColor = colors.surfacePage,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Extraction Results",
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
            label = "result_state",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { state ->
            when (state) {
                is ResultUiState.Loading -> {
                    LoadingContent(
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ResultUiState.Verifying -> {
                    VerifyingContent(
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ResultUiState.Loaded -> {
                    ResultLoadedContent(
                        data = state.data,
                        onShareArtifact = viewModel::shareArtifact,
                        onReExtract = viewModel::reExtractPartition,
                        onBrowseArtifact = onNavigateToFilesystemBrowser,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is ResultUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.initialize(jobId) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

// =============================================================================
// Loading
// =============================================================================

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val colors = ForgeTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = colors.actionPrimaryBg,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading results…",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
    }
}

// =============================================================================
// Verifying — PRD Rule #3: no success before verification
// =============================================================================

@Composable
private fun VerifyingContent(
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
            CircularProgressIndicator(
                color = colors.actionPrimaryBg,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Verifying Integrity",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No results will be marked as complete until verification finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// =============================================================================
// Loaded — full result view
// =============================================================================

@Composable
private fun ResultLoadedContent(
    data: ResultScreenData,
    onShareArtifact: (String) -> Unit,
    onReExtract: (String) -> Unit,
    onBrowseArtifact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    // Determine header visual treatment from job status
    val (headerIcon, headerColor) = resolveHeaderVisuals(data.jobStatus)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // =================================================================
        // Summary header card
        // =================================================================
        item(key = "result_summary") {
            SummaryHeaderCard(
                data = data,
                icon = headerIcon,
                iconColor = headerColor,
            )
        }

        // =================================================================
        // Artifact list header
        // =================================================================
        if (data.artifacts.isNotEmpty()) {
            item(key = "artifacts_header") {
                Text(
                    text = "Extracted Artifacts",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .semantics { heading() },
                )
            }
        }

        // =================================================================
        // Artifact result cards
        // =================================================================
        items(
            items = data.artifacts,
            key = { it.id },
        ) { artifact ->
            ArtifactResultCard(
                artifact = artifact,
                onShare = { onShareArtifact(artifact.id) },
                onReExtract = { onReExtract(artifact.id) },
                onBrowse = { onBrowseArtifact(artifact.id) },
            )
        }

        // =================================================================
        // Empty state
        // =================================================================
        if (data.artifacts.isEmpty() && data.failedCount > 0) {
            item(key = "empty_artifacts") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.feedbackErrorSurface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Error,
                            contentDescription = "No artifacts extracted",
                            modifier = Modifier.size(32.dp),
                            tint = colors.feedbackErrorIcon,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No artifacts were successfully extracted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.feedbackErrorText,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check the extraction logs for details on what went wrong.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Summary header card
// =============================================================================

@Composable
private fun SummaryHeaderCard(
    data: ResultScreenData,
    icon: ImageVector,
    iconColor: Color,
) {
    val colors = ForgeTheme.colors
    val components = ForgeTheme.components

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
            // Status icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = data.summaryText,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary text — the most important line on the screen
            Text(
                text = data.summaryText,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = data.summaryText
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stat badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StatBadge(
                    label = "Verified",
                    count = data.verifiedCount,
                    color = components.status.verifiedIcon,
                )
                if (data.unverifiableCount > 0) {
                    StatBadge(
                        label = "Unverifiable",
                        count = data.unverifiableCount,
                        color = components.status.skippedIcon,
                    )
                }
                if (data.mismatchCount > 0) {
                    StatBadge(
                        label = "Mismatch",
                        count = data.mismatchCount,
                        color = components.alert.warningIcon,
                    )
                }
                if (data.failedCount > 0) {
                    StatBadge(
                        label = "Failed",
                        count = data.failedCount,
                        color = components.status.failedIcon,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Duration: ${data.durationFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ForgeTheme.colors.textTertiary,
        )
    }
}

// =============================================================================
// Artifact result card
// =============================================================================

/**
 * Full artifact result card — the core trust display.
 *
 * Shows at a glance:
 *   - Verification badge (✓ verified, ⚠ mismatch, — unverifiable)
 *   - Partition name + size + derivation type
 *   - Full SHA-256 hash (tappable to copy)
 *   - For mismatch: expected vs actual + re-extract CTA
 *   - For verified: share action
 *
 * Color coding exclusively from component tokens:
 *   Verified → status.verifiedIcon / verifiedText
 *   Mismatch → alert.warningIcon / warningText
 *   Unverifiable → status.skippedIcon / skippedText
 */
@Composable
private fun ArtifactResultCard(
    artifact: ResultArtifactItem,
    onShare: () -> Unit,
    onReExtract: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val components = ForgeTheme.components
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Resolve visual treatment from verification status — never hardcoded colors
    val (badgeIcon, badgeColor, badgeLabel) = resolveVerificationVisuals(
        artifact.verificationStatus,
    )

    // Derivation type badge color
    val derivationColor = when (artifact.derivationType) {
        "DIRECT" -> components.badge.successText
        "RECONSTRUCTED" -> components.badge.warningText
        "RAW_UNVERIFIED" -> components.status.failedText
        else -> colors.textTertiary
    }
    val derivationBgColor = when (artifact.derivationType) {
        "DIRECT" -> components.badge.successBg
        "RECONSTRUCTED" -> components.badge.warningBg
        "RAW_UNVERIFIED" -> colors.feedbackErrorSurface
        else -> colors.surfaceSunken
    }

    var showFullHash by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: badge + name + size
            Row(
                verticalAlignment = Alignment.Top,
            ) {
                // Verification badge icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = badgeLabel,
                        modifier = Modifier.size(20.dp),
                        tint = badgeColor,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Partition name
                    Text(
                        text = "${artifact.partitionName}.img",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    )

                    // Size + derivation row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = artifact.sizeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )

                        // Derivation type badge
                        Text(
                            text = artifact.derivationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = derivationColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(
                                    color = derivationBgColor,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification status label
            Text(
                text = artifact.verificationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium,
            )

            // SHA-256 hash display (tappable to copy and expand)
            if (artifact.sha256.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceSunken)
                        .clickable { showFullHash = !showFullHash }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SHA-256",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        Text(
                            text = if (showFullHash) artifact.sha256
                            else "${artifact.sha256.take(32)}…",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = colors.textSecondary,
                            maxLines = if (showFullHash) 4 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(artifact.sha256))
                            Toast.makeText(context, "SHA-256 copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy SHA-256 hash",
                            modifier = Modifier.size(16.dp),
                            tint = colors.textTertiary,
                        )
                    }
                }
            }

            // Mismatch detail: expected vs actual
            AnimatedVisibility(
                visible = artifact.verificationStatus == VerificationStatus.MISMATCH.name,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.feedbackWarningSurface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = "Hash verification failed",
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.feedbackWarningIcon,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hash Verification Failed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.feedbackWarningText,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Expected hash
                            Text(
                                text = "Expected:",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = artifact.expectedHash.ifBlank { "Not available" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = colors.feedbackWarningText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Actual hash
                            Text(
                                text = "Actual:",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = artifact.sha256.ifBlank { "Not available" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = colors.feedbackWarningText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Unverifiable explanation
            if (artifact.verificationStatus == VerificationStatus.UNVERIFIABLE.name) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = colors.feedbackInfoSurface,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RemoveCircleOutline,
                        contentDescription = "Verification not available",
                        modifier = Modifier.size(16.dp),
                        tint = colors.feedbackInfoIcon,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No target hash in manifest. This format does not " +
                            "provide verification data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.feedbackInfoText,
                    )
                }
            }

            // Warnings list
            if (artifact.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                artifact.warnings.forEach { warning ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null, // Decorative — warning text follows
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp),
                            tint = colors.feedbackWarningIcon,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.feedbackWarningText,
                        )
                    }
                }
            }

            // Source package reference
            artifact.sourcePackageDisplayName?.let { sourceName ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Source: $sourceName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.borderSubtle)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Re-extract button (mismatch only)
                if (artifact.canReExtract) {
                    OutlinedButton(
                        onClick = onReExtract,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.feedbackWarningText,
                        ),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null, // Decorative — button text "Re-extract" conveys meaning
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Re-extract",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Browse button (verified only — PRD FR-8)
                if (artifact.canBrowse) {
                    OutlinedButton(
                        onClick = onBrowse,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textLink,
                        ),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null, // Decorative — button text "Browse" conveys meaning
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Share button (verified and unverifiable)
                if (artifact.canShare) {
                    Button(
                        onClick = onShare,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionPrimaryBg,
                            contentColor = colors.textOnAction,
                        ),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null, // Decorative — button text "Share" conveys meaning
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
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
    onRetry: () -> Unit,
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
                contentDescription = "Error loading results",
                modifier = Modifier.size(48.dp),
                tint = colors.feedbackErrorIcon,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to load results",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionPrimaryBg,
                    contentColor = colors.textOnAction,
                ),
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// =============================================================================
// Visual resolution helpers
// =============================================================================

/**
 * Resolve the header icon and color based on job status.
 * Color coding from component tokens — never hardcoded.
 */
@Composable
private fun resolveHeaderVisuals(jobStatus: String): Pair<ImageVector, Color> {
    val components = ForgeTheme.components
    val colors = ForgeTheme.colors

    return when (jobStatus) {
        JobStatus.COMPLETED.name -> Icons.Outlined.Verified to components.status.verifiedIcon
        JobStatus.PARTIAL_SUCCESS.name -> Icons.Outlined.Warning to components.alert.warningIcon
        JobStatus.FAILED.name -> Icons.Outlined.Error to components.status.failedIcon
        JobStatus.CANCELED.name -> Icons.Outlined.Cancel to colors.textTertiary
        else -> Icons.Outlined.CheckCircle to colors.textTertiary
    }
}

/**
 * Resolve verification badge icon, color, and label.
 * Trust labeling sourced from ArtifactEntity.verificationStatus —
 * never inferred in the UI layer.
 */
@Composable
private fun resolveVerificationVisuals(
    status: String,
): Triple<ImageVector, Color, String> {
    val components = ForgeTheme.components
    val colors = ForgeTheme.colors

    return when (status) {
        VerificationStatus.VERIFIED.name -> Triple(
            Icons.Outlined.Verified,
            components.status.verifiedIcon,
            "Verified",
        )
        VerificationStatus.MISMATCH.name -> Triple(
            Icons.Outlined.Warning,
            components.alert.warningIcon,
            "Hash mismatch",
        )
        VerificationStatus.UNVERIFIABLE.name -> Triple(
            Icons.Outlined.RemoveCircleOutline,
            components.status.skippedIcon,
            "Unverifiable",
        )
        else -> Triple(
            Icons.Outlined.CheckCircle,
            components.status.pendingIcon,
            "Pending",
        )
    }
}
