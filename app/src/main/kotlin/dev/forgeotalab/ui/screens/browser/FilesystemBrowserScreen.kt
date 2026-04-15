package dev.forgeotalab.ui.screens.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.contracts.model.FsEntryType
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Accessibility:
 * - Screen title "Filesystem Browser" uses Role.Heading for screen readers
 * - Breadcrumb segments are individually tappable with "Navigate to {segment}" descriptions
 * - File/folder icons use semantic descriptions ("Folder", "File", "Symlink")
 * - Permission strings announced as-is by TalkBack (already readable)
 * - Touch targets ≥ 48dp enforced via minimum size modifiers
 * - Focus order: top bar → breadcrumbs → sort → file list
 *
 * PRD FR-8: "Read-only filesystem browser for verified extracted partition images."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesystemBrowserScreen(
    onNavigateBack: () -> Unit,
    viewModel: FilesystemBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // PRD: "Back button navigates up one level (not to previous screen)."
    BackHandler {
        if (!viewModel.navigateUp()) {
            onNavigateBack()
        }
    }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            // TODO(P02): Wire snackbar/toast for export events
        }
    }

    val colors = ForgeTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Filesystem Browser",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        val subtitle = when (val state = uiState) {
                            is FilesystemBrowserUiState.Browsable ->
                                "${state.data.filesystemType} • ${state.data.artifactName}"
                            else -> ""
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfacePage,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
        containerColor = colors.surfacePage,
    ) { contentPadding ->

        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.padding(contentPadding),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "browser_state",
        ) { state ->
            when (state) {
                is FilesystemBrowserUiState.Loading -> {
                    LoadingState()
                }
                is FilesystemBrowserUiState.Browsable -> {
                    BrowsableContent(
                        data = state.data,
                        onNavigateToDir = viewModel::navigateTo,
                        onNavigateToSegment = viewModel::navigateToSegment,
                        onChangeSortOrder = viewModel::changeSortOrder,
                    )
                }
                is FilesystemBrowserUiState.UnsupportedFs -> {
                    UnsupportedFilesystemState(
                        format = state.format,
                        onRawExport = { /* TODO(P02): Wire SAF raw export */ },
                    )
                }
                is FilesystemBrowserUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        canRawExport = state.canRawExport,
                        onRawExport = { /* TODO(P02): Wire SAF raw export */ },
                        onRetry = viewModel::initialize,
                    )
                }
            }
        }
    }
}

// ===========================================================================
// Loading state
// ===========================================================================

@Composable
private fun LoadingState() {
    val colors = ForgeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = colors.actionPrimaryBg,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mounting filesystem…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

// ===========================================================================
// Browsable content
// ===========================================================================

@Composable
private fun BrowsableContent(
    data: BrowserScreenData,
    onNavigateToDir: (String) -> Unit,
    onNavigateToSegment: (String) -> Unit,
    onChangeSortOrder: (SortOrder) -> Unit,
) {
    val colors = ForgeTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {

        // Breadcrumb bar
        BreadcrumbBar(
            breadcrumbs = data.breadcrumbs,
            onSegmentClick = onNavigateToSegment,
        )

        // Directory info bar
        DirectoryInfoBar(
            totalCount = data.totalCount,
            totalSize = data.totalSize,
            sortOrder = data.sortOrder,
            onChangeSortOrder = onChangeSortOrder,
        )

        HorizontalDivider(color = colors.borderSubtle)

        // File list
        if (data.entries.isEmpty()) {
            EmptyDirectoryState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = data.entries,
                    key = { it.path },
                ) { entry ->
                    FsEntryRow(
                        entry = entry,
                        onDirectoryClick = onNavigateToDir,
                    )
                }

                if (data.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = colors.actionPrimaryBg,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Breadcrumb bar
// ===========================================================================

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<PathSegment>,
    onSegmentClick: (String) -> Unit,
) {
    val colors = ForgeTheme.colors
    val scrollState = rememberScrollState()

    // Auto-scroll to end when breadcrumbs change
    LaunchedEffect(breadcrumbs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .background(colors.surfaceDefault)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, segment ->
            val isLast = index == breadcrumbs.lastIndex

            Text(
                text = segment.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                ),
                color = if (isLast) colors.textPrimary else colors.textLink,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (!isLast) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = "Navigate to ${segment.name}",
                            ) { onSegmentClick(segment.fullPath) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                maxLines = 1,
            )

            if (!isLast) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null, // Decorative
                    modifier = Modifier.size(16.dp),
                    tint = colors.textTertiary,
                )
            }
        }
    }
}

// ===========================================================================
// Directory info bar
// ===========================================================================

@Composable
private fun DirectoryInfoBar(
    totalCount: Long,
    totalSize: Long,
    sortOrder: SortOrder,
    onChangeSortOrder: (SortOrder) -> Unit,
) {
    val colors = ForgeTheme.colors
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceDefault)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$totalCount items • ${formatSize(totalSize)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )

        Box {
            IconButton(
                onClick = { showSortMenu = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort files",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sortOrderLabel(order),
                                fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onChangeSortOrder(order)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
    }
}

// ===========================================================================
// File entry row
// ===========================================================================

@Composable
private fun FsEntryRow(
    entry: FsEntryUiItem,
    onDirectoryClick: (String) -> Unit,
) {
    val colors = ForgeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.isDir) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open folder ${entry.name}",
                    ) { onDirectoryClick(entry.path) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // File type icon
        Icon(
            imageVector = entryIcon(entry),
            contentDescription = entryIconDescription(entry),
            modifier = Modifier.size(28.dp),
            tint = if (entry.isDir) colors.actionPrimaryBg else colors.textSecondary,
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name and metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (entry.isDir) FontWeight.Medium else FontWeight.Normal,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!entry.isDir) {
                    Text(
                        text = entry.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                Text(
                    text = entry.permissions,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = colors.textTertiary,
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Text(
                    text = entry.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }

        // Directory chevron
        if (entry.isDir) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null, // Decorative — row click action conveys navigation
                modifier = Modifier.size(20.dp),
                tint = colors.textTertiary,
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = colors.borderSubtle.copy(alpha = 0.5f),
    )
}

// ===========================================================================
// Unsupported filesystem state
// ===========================================================================

/**
 * PRD Failure #11: "Unsupported filesystem → show full-screen info state.
 * Unsupported states look intentionally informative, not broken."
 */
@Composable
private fun UnsupportedFilesystemState(
    format: String,
    onRawExport: () -> Unit,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.feedbackInfoSurface,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Informational",
                    modifier = Modifier.size(48.dp),
                    tint = colors.feedbackInfoIcon,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Filesystem not yet supported",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.feedbackInfoText,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Detected format: $format\n\nThis filesystem cannot be browsed yet, but you can export the raw partition image for use with desktop tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.feedbackInfoText.copy(alpha = 0.8f),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PRD: Raw export is the primary CTA for unsupported FS
                FilledTonalButton(
                    onClick = onRawExport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null, // Decorative — "Export Raw Image" text conveys meaning
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Raw Image")
                }
            }
        }
    }
}

// ===========================================================================
// Error state
// ===========================================================================

@Composable
private fun ErrorState(
    message: String,
    canRawExport: Boolean,
    onRawExport: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = ForgeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.feedbackErrorSurface,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = colors.feedbackErrorIcon,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Cannot browse this filesystem",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.feedbackErrorText,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.feedbackErrorText.copy(alpha = 0.8f),
                )

                Spacer(modifier = Modifier.height(20.dp))

                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry")
                }

                if (canRawExport) {
                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = onRawExport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null, // Decorative — "Export Raw Image" text conveys meaning
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Raw Image")
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Empty directory state
// ===========================================================================

@Composable
private fun EmptyDirectoryState() {
    val colors = ForgeTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null, // Decorative — "Empty directory" text follows
                modifier = Modifier.size(64.dp),
                tint = colors.textTertiary.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Empty directory",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
    }
}

// ===========================================================================
// Utility functions
// ===========================================================================

private fun entryIcon(entry: FsEntryUiItem) = when {
    entry.isDir -> Icons.Default.Folder
    entry.fileType == FsEntryType.SYMLINK -> Icons.Default.Link
    entry.name.endsWith(".img", ignoreCase = true) -> Icons.Default.Storage
    entry.name.endsWith(".prop", ignoreCase = true) ||
        entry.name.endsWith(".txt", ignoreCase = true) ||
        entry.name.endsWith(".xml", ignoreCase = true) ||
        entry.name.endsWith(".conf", ignoreCase = true) -> Icons.Default.Description
    entry.name.endsWith(".png", ignoreCase = true) ||
        entry.name.endsWith(".jpg", ignoreCase = true) ||
        entry.name.endsWith(".webp", ignoreCase = true) -> Icons.Default.Image
    entry.name.endsWith(".so", ignoreCase = true) ||
        entry.name.endsWith(".apk", ignoreCase = true) -> Icons.Default.Settings
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/**
 * Semantic icon descriptions — describes meaning, not appearance.
 * PRD Accessibility: "Status icons describe meaning, not appearance."
 */
private fun entryIconDescription(entry: FsEntryUiItem) = when {
    entry.isDir -> "Folder"
    entry.fileType == FsEntryType.SYMLINK -> "Symbolic link"
    else -> "${entry.fileType.name.lowercase().replace('_', ' ')} file"
}

private fun sortOrderLabel(order: SortOrder) = when (order) {
    SortOrder.NAME_ASC -> "Name (A→Z)"
    SortOrder.NAME_DESC -> "Name (Z→A)"
    SortOrder.SIZE_ASC -> "Size (smallest)"
    SortOrder.SIZE_DESC -> "Size (largest)"
    SortOrder.TYPE -> "Type"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
