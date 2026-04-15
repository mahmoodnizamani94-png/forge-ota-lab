package dev.forgeotalab.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hardware
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.ui.components.ClassificationBadge
import dev.forgeotalab.ui.components.ResumeBanner
import dev.forgeotalab.ui.components.SupportTierBadge
import dev.forgeotalab.ui.components.UnavailableBadge
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Home screen — job history and import entry point.
 *
 * Accessibility:
 *   - Screen title "Forge OTA Lab" announced with Role.Heading
 *   - Import FAB has contentDescription "Import OTA package"
 *   - History cards have contentDescription with package name and tier
 *   - Swipe-to-delete announces "Deleted {name}" via Snackbar
 *   - Unavailable items announce "File unavailable" badge
 *   - Resume banner announces interruption state and available actions
 *   - Touch targets ≥ 48dp on all interactive elements
 *
 * States (PRD State Matrix, Home row):
 *   - Empty: Branded illustration + format support card + Import CTA
 *   - Populated: Top bar + Import FAB + interrupted banner + history list
 *   - Error: "Unable to load history. Tap to retry."
 *   - Loading: Spinner during initial history load
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pendingImportUri: Uri? = null,
    onPendingImportConsumed: () -> Unit = {},
    onNavigateToAnalysis: (packageId: String) -> Unit = {},
    onNavigateToResult: (jobId: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val colors = ForgeTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val resumeState by viewModel.resumeState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importFile(it) }
    }

    // Handle pending import URI from share/open-with intent
    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            viewModel.importFile(uri)
            onPendingImportConsumed()
        }
    }

    // Handle import result — navigate to analysis on success
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                onNavigateToAnalysis(state.packageId)
                viewModel.consumeImportResult()
            }
            is ImportState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.consumeImportResult()
            }
            else -> {}
        }
    }

    // Handle resume result — navigate or show snackbar
    LaunchedEffect(resumeState) {
        when (val state = resumeState) {
            is ResumeUiState.Resumed -> {
                snackbarHostState.showSnackbar(
                    "Resuming extraction (${state.skippedCount} partitions already done)",
                )
                viewModel.consumeResumeResult()
            }
            is ResumeUiState.SourceRevoked -> {
                snackbarHostState.showSnackbar(
                    "Source file \"${state.packageDisplayName}\" is no longer accessible. " +
                        "Re-import the file to continue.",
                )
                viewModel.consumeResumeResult()
            }
            is ResumeUiState.CheckpointCorrupted -> {
                snackbarHostState.showSnackbar(
                    "Recovery data is corrupted. Use Clean up to preserve verified outputs.",
                )
                viewModel.consumeResumeResult()
            }
            is ResumeUiState.CleanedUp -> {
                val msg = if (state.preservedCount > 0) {
                    "Cleaned up. ${state.preservedCount} verified output(s) preserved."
                } else {
                    "Cleaned up. No outputs to preserve."
                }
                snackbarHostState.showSnackbar(msg)
                viewModel.consumeResumeResult()
            }
            is ResumeUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.consumeResumeResult()
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = colors.surfacePage,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState is HomeUiState.Loaded) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Forge OTA Lab",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = colors.textSecondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surfacePage,
                        titleContentColor = colors.textPrimary,
                    ),
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null, // Decorative — "Import OTA" text label conveys meaning
                    )
                },
                text = { Text("Import OTA") },
                containerColor = colors.actionPrimaryBg,
                contentColor = colors.textOnAction,
            )
        },
    ) { innerPadding ->
        // Analyzing overlay
        if (importState is ImportState.Analyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(colors.overlayScrim),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = colors.actionPrimaryBg,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Analyzing file…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                }
            }
        }

        AnimatedContent(
            targetState = uiState,
            label = "home_state",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { state ->
            when (state) {
                is HomeUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = colors.actionPrimaryBg,
                        )
                    }
                }

                is HomeUiState.Empty -> {
                    EmptyHomeContent(
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is HomeUiState.Loaded -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp, // Space for FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Interrupted jobs banner (FR-9)
                        if (state.interruptedJobs.isNotEmpty()) {
                            item(key = "interrupted_banner") {
                                val firstInterrupted = state.interruptedJobs.first()
                                ResumeBanner(
                                    packageDisplayName = firstInterrupted.packageDisplayName,
                                    completedPartitions = firstInterrupted.completedPartitions,
                                    totalPartitions = firstInterrupted.totalPartitions,
                                    onResume = { viewModel.resumeJob(firstInterrupted.jobId) },
                                    onCleanup = { viewModel.cleanupJob(firstInterrupted.jobId) },
                                )
                            }
                        }

                        // History items with swipe-to-delete (FR-11)
                        items(
                            items = state.packages,
                            key = { it.id },
                        ) { pkg ->
                            SwipeToDeleteHistoryCard(
                                item = pkg,
                                snackbarHostState = snackbarHostState,
                                onDelete = { viewModel.deleteHistoryItem(pkg.id) },
                                onUndo = { viewModel.undoDeleteHistoryItem() },
                                onClick = {
                                    when (val target = viewModel.getNavigationTarget(pkg)) {
                                        is NavigationTarget.Analysis -> {
                                            onNavigateToAnalysis(target.packageId)
                                        }
                                        is NavigationTarget.Result -> {
                                            onNavigateToResult(target.jobId)
                                        }
                                        is NavigationTarget.Interrupted -> {
                                            viewModel.resumeJob(target.jobId)
                                        }
                                        is NavigationTarget.ActiveJob -> {
                                            onNavigateToResult(target.jobId)
                                        }
                                        is NavigationTarget.UriRevoked -> {
                                            // Could show a dialog — for now, open file picker
                                            filePickerLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/octet-stream",
                                                    "*/*",
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.retryLoadHistory() }) {
                                Text("Retry", color = colors.actionPrimaryBg)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Swipe-to-delete wrapper
// =============================================================================

/**
 * History card wrapped in SwipeToDismissBox for swipe-to-delete (FR-11).
 *
 * The dismiss background shows a red delete icon. On dismiss, the item is
 * deleted and a Snackbar with undo action appears.
 *
 * Accessibility: Swipe action is announced as "Delete" by TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteHistoryCard(
    item: PackageHistoryItem,
    snackbarHostState: SnackbarHostState,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = ForgeTheme.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    // Show Snackbar with undo when dismissed
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            val result = snackbarHostState.showSnackbar(
                message = "\"${item.displayName}\" removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Delete background — visible during swipe
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.feedbackErrorIcon),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.padding(end = 20.dp),
                    tint = Color.White,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        PackageHistoryCard(
            item = item,
            onClick = onClick,
        )
    }
}

// =============================================================================
// Empty state content — branded, designed, not a skeleton
// =============================================================================

/**
 * Empty home state per PRD Loading/Empty/Error Patterns:
 * "Branded illustration + format support examples + Import CTA"
 */
@Composable
private fun EmptyHomeContent(
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Forge anvil icon — brand mark
        Icon(
            imageVector = Icons.Outlined.Hardware,
            contentDescription = null, // Decorative — title text provides meaning
            modifier = Modifier.size(72.dp),
            tint = colors.actionPrimaryBg,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Forge OTA Lab",
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
            modifier = Modifier.semantics {
                heading()
                role = Role.Image
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Android OTA Extraction Workbench",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Format support examples card
        FormatSupportCard()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Select an OTA package to begin analysis",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Format support card showing tier examples for first-time users.
 *
 * WHY here: The PRD specifies the empty state should include
 * "format support examples" — not just a blank screen with a button.
 */
@Composable
private fun FormatSupportCard() {
    val colors = ForgeTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Supported Formats",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            FormatRow(
                tier = SupportTier.SUPPORTED,
                description = "Google Pixel & standard payload-based full OTAs",
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatRow(
                tier = SupportTier.EXPERIMENTAL,
                description = "Incremental OTAs with validated base images",
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatRow(
                tier = SupportTier.FORENSIC,
                description = "Unknown packages — metadata inspection & diagnostics",
            )
        }
    }
}

@Composable
private fun FormatRow(
    tier: SupportTier,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SupportTierBadge(tier = tier)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = ForgeTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

// =============================================================================
// Package history card — individual history list item
// =============================================================================

/**
 * History item card showing package summary with support tier badge.
 *
 * Now includes:
 * - Unavailable badge for revoked URIs (FR-11)
 * - Job status badge for completed/interrupted jobs
 * - Dimmed appearance for inaccessible items
 *
 * Accessibility:
 *   - Card announces: "$displayName, $tier, $classification"
 *   - Unavailable items announce: "File unavailable"
 *   - Touch target covers entire card surface (≥ 48dp)
 */
@Composable
fun PackageHistoryCard(
    item: PackageHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val alpha = if (item.isUriAccessible) 1.0f else 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault.copy(alpha = alpha),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // File icon
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null, // Decorative — item name text conveys meaning
                modifier = Modifier.size(40.dp),
                tint = colors.actionPrimaryBg.copy(alpha = alpha),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SupportTierBadge(tier = item.supportTier)
                    ClassificationBadge(classification = item.classification)

                    // Unavailable badge for revoked URIs
                    if (!item.isUriAccessible) {
                        UnavailableBadge()
                    }

                    // Job status badge
                    item.latestJobStatus?.let { status ->
                        JobStatusBadge(status = status)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = item.fileSizeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary.copy(alpha = alpha),
                    )
                    Text(
                        text = item.lastOpenedFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary.copy(alpha = alpha),
                    )
                    item.securityPatchLevel?.let { patchLevel ->
                        Text(
                            text = patchLevel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary.copy(alpha = alpha),
                        )
                    }
                }
            }

            // Info icon
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "View analysis details for ${item.displayName}",
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp),
                tint = colors.textTertiary.copy(alpha = alpha),
            )
        }
    }
}

// =============================================================================
// Job status badge — inline status indicator
// =============================================================================

/**
 * Small badge showing job status on history cards.
 *
 * Only renders for notable statuses — completed is the default state
 * and doesn't need a badge.
 */
@Composable
private fun JobStatusBadge(
    status: dev.forgeotalab.contracts.model.JobStatus,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val (text, color) = when (status) {
        dev.forgeotalab.contracts.model.JobStatus.COMPLETED -> return // No badge for success
        dev.forgeotalab.contracts.model.JobStatus.PARTIAL_SUCCESS -> "Partial" to colors.feedbackWarningIcon
        dev.forgeotalab.contracts.model.JobStatus.INTERRUPTED -> "Interrupted" to colors.feedbackWarningIcon
        dev.forgeotalab.contracts.model.JobStatus.PAUSED -> "Paused" to colors.textTertiary
        dev.forgeotalab.contracts.model.JobStatus.RUNNING -> "Running" to colors.actionPrimaryBg
        dev.forgeotalab.contracts.model.JobStatus.QUEUED -> "Queued" to colors.textTertiary
        dev.forgeotalab.contracts.model.JobStatus.FAILED -> "Failed" to colors.feedbackErrorIcon
        dev.forgeotalab.contracts.model.JobStatus.CANCELED -> "Canceled" to colors.textTertiary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
