package dev.forgeotalab.ui.screens.analysis

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.contracts.model.PartitionCategory
import dev.forgeotalab.contracts.model.SupportTier
import dev.forgeotalab.data.entity.PartitionEntity
import dev.forgeotalab.ui.components.SupportTierBadge
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Analysis screen — displays the full analysis result for an OTA package.
 *
 * Accessibility:
 *   - Screen title announces package name + tier for TalkBack
 *   - Partition cards have semantic content descriptions
 *   - Category group headers use heading semantics
 *   - Storage estimate announces deficit/surplus
 *   - Touch targets ≥ 48dp on all interactive elements
 *
 * States (PRD State Matrix, Analysis row):
 *   - Loading: "Analyzing file…" spinner
 *   - Supported: Full inventory with partition selection + extract CTA
 *   - Experimental: Same as Supported with experimental badging
 *   - Forensic: Inspection only, no extract CTA
 *   - Corrupted/FormatUnknown: Error with diagnostic info
 *   - Error: DB/system error with retry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    packageId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExtraction: (String) -> Unit = {},
    onNavigateToIncrementalWizard: (String) -> Unit = {},
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val colors = ForgeTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPartitions by viewModel.selectedPartitions.collectAsStateWithLifecycle()
    val storageEstimate by viewModel.storageEstimate.collectAsStateWithLifecycle()

    LaunchedEffect(packageId) {
        viewModel.loadAnalysis(packageId)
    }

    Scaffold(
        containerColor = colors.surfacePage,
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val state = uiState) {
                        is AnalysisUiState.Supported -> state.data.displayName
                        is AnalysisUiState.Experimental -> state.data.displayName
                        is AnalysisUiState.Forensic -> state.data.displayName
                        else -> "Analysis"
                    }
                    Text(
                        text = titleText,
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
            label = "analysis_state",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { state ->
            when (state) {
                is AnalysisUiState.Loading -> {
                    LoadingContent(
                        phase = state.phase,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.Supported -> {
                    AnalysisContent(
                        data = state.data,
                        selectedPartitions = selectedPartitions,
                        storageEstimate = storageEstimate,
                        onTogglePartition = viewModel::togglePartition,
                        onApplyBootPreset = viewModel::applyBootPreset,
                        onApplySystemPreset = viewModel::applySystemPreset,
                        onApplyEverythingPreset = viewModel::applyEverythingPreset,
                        onClearSelection = viewModel::clearSelection,
                        onExtract = { onNavigateToExtraction(state.data.packageId) },
                        showExtractButton = true,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.Experimental -> {
                    AnalysisContent(
                        data = state.data,
                        selectedPartitions = selectedPartitions,
                        storageEstimate = storageEstimate,
                        onTogglePartition = viewModel::togglePartition,
                        onApplyBootPreset = viewModel::applyBootPreset,
                        onApplySystemPreset = viewModel::applySystemPreset,
                        onApplyEverythingPreset = viewModel::applyEverythingPreset,
                        onClearSelection = viewModel::clearSelection,
                        onExtract = {
                            // WHY route to wizard: Incremental packages in Experimental tier
                            // must validate base images before extraction. FR-4 requires the
                            // prerequisite wizard to gate extraction.
                            if (state.data.isIncremental) {
                                onNavigateToIncrementalWizard(state.data.packageId)
                            } else {
                                onNavigateToExtraction(state.data.packageId)
                            }
                        },
                        showExtractButton = true,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.Forensic -> {
                    ForensicContent(
                        data = state.data,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.Corrupted -> {
                    ErrorDetailContent(
                        title = "Corrupted Package",
                        message = state.message,
                        magicBytes = state.magicBytes,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.FormatUnknown -> {
                    ErrorDetailContent(
                        title = "Unknown Format",
                        message = state.message,
                        magicBytes = state.magicBytes,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is AnalysisUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(48.dp),
                                tint = colors.feedbackErrorIcon,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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
    phase: String,
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
                text = phase,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
    }
}

// =============================================================================
// Analysis content — Supported & Experimental states
// =============================================================================

@Composable
private fun AnalysisContent(
    data: AnalysisScreenData,
    selectedPartitions: Set<String>,
    storageEstimate: dev.forgeotalab.contracts.model.StorageEstimate?,
    onTogglePartition: (String) -> Unit,
    onApplyBootPreset: () -> Unit,
    onApplySystemPreset: () -> Unit,
    onApplyEverythingPreset: () -> Unit,
    onClearSelection: () -> Unit,
    onExtract: () -> Unit,
    showExtractButton: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.screenWidthDp >= 600

    if (isWideLayout) {
        // Two-column layout for landscape/tablet
        // PRD: "Phone landscape → two-column, Tablet → master-detail"
        Row(
            modifier = modifier.fillMaxSize(),
        ) {
            // Left column: Package metadata + presets + storage
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PackageHeaderCard(data = data)
                PresetChips(
                    onBootPreset = onApplyBootPreset,
                    onSystemPreset = onApplySystemPreset,
                    onEverythingPreset = onApplyEverythingPreset,
                    onClear = onClearSelection,
                )
                storageEstimate?.let { estimate ->
                    StorageEstimateBar(
                        estimate = estimate,
                        selectedCount = selectedPartitions.size,
                    )
                }
                if (showExtractButton) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onExtract,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedPartitions.isNotEmpty() && (storageEstimate?.isSufficient != false),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionPrimaryBg,
                            contentColor = colors.textOnAction,
                            disabledContainerColor = colors.actionPrimaryBgDisabled,
                            disabledContentColor = colors.actionPrimaryTextDisabled,
                        ),
                    ) {
                        Text(
                            text = if (selectedPartitions.isEmpty()) {
                                "Select partitions to extract"
                            } else {
                                "Extract ${selectedPartitions.size} partitions"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // Right column: Partition list
            LazyColumn(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data.partitionGroups.forEach { (category, partitions) ->
                    item(key = "group_${category.name}") {
                        PartitionGroupHeader(
                            category = category,
                            count = partitions.size,
                        )
                    }
                    partitions.forEach { partition ->
                        item(key = "partition_${partition.id}") {
                            PartitionCard(
                                partition = partition,
                                isSelected = selectedPartitions.contains(partition.id),
                                onToggle = { onTogglePartition(partition.id) },
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Single-column layout for phone portrait
        AnalysisContentCompact(
            data = data,
            selectedPartitions = selectedPartitions,
            storageEstimate = storageEstimate,
            onTogglePartition = onTogglePartition,
            onApplyBootPreset = onApplyBootPreset,
            onApplySystemPreset = onApplySystemPreset,
            onApplyEverythingPreset = onApplyEverythingPreset,
            onClearSelection = onClearSelection,
            onExtract = onExtract,
            showExtractButton = showExtractButton,
            modifier = modifier,
        )
    }
}

/**
 * Compact single-column layout for phone portrait — the original AnalysisContent layout.
 */
@Composable
private fun AnalysisContentCompact(
    data: AnalysisScreenData,
    selectedPartitions: Set<String>,
    storageEstimate: dev.forgeotalab.contracts.model.StorageEstimate?,
    onTogglePartition: (String) -> Unit,
    onApplyBootPreset: () -> Unit,
    onApplySystemPreset: () -> Unit,
    onApplyEverythingPreset: () -> Unit,
    onClearSelection: () -> Unit,
    onExtract: () -> Unit,
    showExtractButton: Boolean,
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
        item(key = "header") {
            PackageHeaderCard(data = data)
        }

        item(key = "presets") {
            PresetChips(
                onBootPreset = onApplyBootPreset,
                onSystemPreset = onApplySystemPreset,
                onEverythingPreset = onApplyEverythingPreset,
                onClear = onClearSelection,
            )
        }

        if (storageEstimate != null) {
            item(key = "storage") {
                StorageEstimateBar(
                    estimate = storageEstimate,
                    selectedCount = selectedPartitions.size,
                )
            }
        }

        data.partitionGroups.forEach { (category, partitions) ->
            item(key = "group_${category.name}") {
                PartitionGroupHeader(
                    category = category,
                    count = partitions.size,
                )
            }
            partitions.forEach { partition ->
                item(key = "partition_${partition.id}") {
                    PartitionCard(
                        partition = partition,
                        isSelected = selectedPartitions.contains(partition.id),
                        onToggle = { onTogglePartition(partition.id) },
                    )
                }
            }
        }

        if (showExtractButton) {
            item(key = "extract_button") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onExtract,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedPartitions.isNotEmpty() && (storageEstimate?.isSufficient != false),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.actionPrimaryBg,
                        contentColor = colors.textOnAction,
                        disabledContainerColor = colors.actionPrimaryBgDisabled,
                        disabledContentColor = colors.actionPrimaryTextDisabled,
                    ),
                ) {
                    Text(
                        text = if (selectedPartitions.isEmpty()) {
                            "Select partitions to extract"
                        } else {
                            "Extract ${selectedPartitions.size} partitions"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
// =============================================================================
// Package header card
// =============================================================================

@Composable
private fun PackageHeaderCard(
    data: AnalysisScreenData,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

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
            // Tier badge + OTA type row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SupportTierBadge(tier = data.supportTier)

                Text(
                    text = if (data.isIncremental) "Incremental OTA" else "Full OTA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .background(
                            color = colors.surfaceRaised,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Package family
            Text(
                text = data.packageFamily,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata rows
            MetadataRow(label = "File size", value = data.fileSizeFormatted)

            data.securityPatchLevel?.let {
                MetadataRow(label = "Security patch", value = it)
            }

            data.deviceModel?.let {
                MetadataRow(label = "Device", value = it)
            }

            MetadataRow(
                label = "Partitions",
                value = "${data.extractableCount} extractable of ${data.totalPartitionCount} total",
            )

            MetadataRow(
                label = "Total extracted size",
                value = data.totalExtractedSizeFormatted,
            )

            // Incremental warning
            if (data.isIncremental) {
                Spacer(modifier = Modifier.height(8.dp))
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
                        contentDescription = "Incremental OTA warning",
                        modifier = Modifier.size(20.dp),
                        tint = colors.feedbackWarningIcon,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Incremental OTA — some partitions require a validated base image for reconstruction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.feedbackWarningText,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ForgeTheme.colors.textTertiary,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ForgeTheme.colors.textSecondary,
        )
    }
}

// =============================================================================
// Preset chips
// =============================================================================

@Composable
private fun PresetChips(
    onBootPreset: () -> Unit,
    onSystemPreset: () -> Unit,
    onEverythingPreset: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Quick Selection",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = onBootPreset,
                label = { Text("Boot set", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceDefault,
                    labelColor = colors.textSecondary,
                ),
            )
            FilterChip(
                selected = false,
                onClick = onSystemPreset,
                label = { Text("System", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceDefault,
                    labelColor = colors.textSecondary,
                ),
            )
            FilterChip(
                selected = false,
                onClick = onEverythingPreset,
                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceDefault,
                    labelColor = colors.textSecondary,
                ),
            )
            FilterChip(
                selected = false,
                onClick = onClear,
                label = { Text("Clear", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceDefault,
                    labelColor = colors.textTertiary,
                ),
            )
        }
    }
}

// =============================================================================
// Storage estimate bar
// =============================================================================

@Composable
fun StorageEstimateBar(
    estimate: dev.forgeotalab.contracts.model.StorageEstimate,
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val barColor = when {
        !estimate.isSufficient -> colors.feedbackErrorIcon
        estimate.requiredBytes > estimate.availableBytes * 0.8 -> colors.feedbackWarningIcon
        else -> colors.feedbackSuccessIcon
    }

    val ratio = if (estimate.availableBytes > 0) {
        (estimate.requiredBytes.toFloat() / estimate.availableBytes).coerceIn(0f, 1f)
    } else 1f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = formatStorageSize(estimate.requiredBytes) + " / " +
                        formatStorageSize(estimate.availableBytes) + " available",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = colors.surfaceSunken,
                        shape = RoundedCornerShape(3.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .background(
                            color = barColor,
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }

            // Deficit warning
            if (!estimate.isSufficient) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Insufficient storage: need ${formatStorageSize(estimate.deficitBytes)} more space. Free storage or select fewer partitions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.feedbackErrorText,
                    modifier = Modifier.semantics {
                        contentDescription = "Warning: insufficient storage. Need ${formatStorageSize(estimate.deficitBytes)} more space."
                    },
                )
            }
        }
    }
}

// =============================================================================
// Partition group header
// =============================================================================

@Composable
fun PartitionGroupHeader(
    category: PartitionCategory,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val (icon, label) = when (category) {
        PartitionCategory.BOOT_CRITICAL -> Icons.Outlined.Verified to "Boot Critical"
        PartitionCategory.LOGICAL_SYSTEM -> Icons.Outlined.CheckCircle to "Logical System"
        PartitionCategory.FIRMWARE_RADIO -> Icons.Outlined.Science to "Firmware / Radio"
        PartitionCategory.METADATA -> Icons.Outlined.Info to "Metadata"
        PartitionCategory.MISC -> Icons.Outlined.Info to "Other"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Decorative — group label text conveys category
            modifier = Modifier.size(18.dp),
            tint = colors.textSecondary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

// =============================================================================
// Individual partition card
// =============================================================================

@Composable
fun PartitionCard(
    partition: PartitionEntity,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                colors.surfaceRaised
            } else {
                colors.surfaceDefault
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Checkbox
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = partition.isExtractable,
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = colors.actionPrimaryBg,
                    uncheckedColor = colors.borderDefault,
                    disabledCheckedColor = colors.actionPrimaryBgDisabled,
                    disabledUncheckedColor = colors.borderDisabled,
                    checkmarkColor = colors.textOnAction,
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Partition info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partition.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (partition.isExtractable) {
                        colors.textPrimary
                    } else {
                        colors.textDisabled
                    },
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatStorageSize(partition.estimatedExtractedSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = "${partition.operationCount} ops",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    partition.compressAlgorithm?.let { algo ->
                        Text(
                            text = algo,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                }

                // Blocking reason for non-extractable partitions
                if (!partition.isExtractable && partition.notExtractableReason != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = partition.notExtractableReason!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.feedbackWarningText,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Forensic content — inspection only, no extraction CTA
// =============================================================================

/**
 * Forensic mode content — informational, never green.
 *
 * PRD Non-Negotiable: "Unknown formats never map to success visuals."
 */
@Composable
private fun ForensicContent(
    data: AnalysisScreenData,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "forensic_header") {
            PackageHeaderCard(data = data)
        }

        item(key = "forensic_info") {
            ForensicInfoCard(data = data)
        }

        item(key = "forensic_note") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.feedbackInfoSurface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null, // Decorative — forensic mode explanation text follows
                        modifier = Modifier.size(20.dp),
                        tint = colors.feedbackInfoIcon,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Forensic mode — metadata inspection only. Extraction is not available for this package format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.feedbackInfoText,
                    )
                }
            }
        }

        // Show partitions in read-only mode if available
        if (data.partitionGroups.isNotEmpty()) {
            data.partitionGroups.forEach { (category, partitions) ->
                item(key = "forensic_group_${category.name}") {
                    PartitionGroupHeader(category = category, count = partitions.size)
                }
                partitions.forEach { partition ->
                    item(key = "forensic_partition_${partition.id}") {
                        PartitionCard(
                            partition = partition,
                            isSelected = false,
                            onToggle = {}, // Read-only in forensic mode
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Forensic info card
// =============================================================================

@Composable
fun ForensicInfoCard(
    data: AnalysisScreenData,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detected Format Markers",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(8.dp))

            MetadataRow(
                label = "Package family",
                value = data.packageFamily,
            )

            data.detectedMagicBytes?.let {
                MetadataRow(
                    label = "Magic bytes",
                    value = it,
                )
            }

            MetadataRow(
                label = "Classification",
                value = data.classification,
            )

            MetadataRow(
                label = "Total partitions",
                value = "${data.totalPartitionCount}",
            )
        }
    }
}

// =============================================================================
// Error detail content — Corrupted & FormatUnknown
// =============================================================================

@Composable
private fun ErrorDetailContent(
    title: String,
    message: String,
    magicBytes: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = "Error",
            modifier = Modifier.size(56.dp),
            tint = colors.feedbackErrorIcon,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        magicBytes?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Detected magic bytes: $it",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Try downloading the file again, or verify the file format.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

// =============================================================================
// Utility
// =============================================================================

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1024} KB"
        bytes < 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    }
}
