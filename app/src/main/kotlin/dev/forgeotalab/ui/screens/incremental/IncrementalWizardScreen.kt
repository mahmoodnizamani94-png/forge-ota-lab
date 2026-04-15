package dev.forgeotalab.ui.screens.incremental

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.ui.components.SupportTierBadge
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Incremental prerequisite wizard screen.
 *
 * Accessibility comment block:
 * - Screen title "Incremental Prerequisite Wizard" announced on navigation
 * - Each partition card: heading semantics with validation status
 * - Mismatch descriptions are full semantic text for TalkBack
 * - Status icons: "Validated" / "Mismatch" / "Base image required" — describes
 *   meaning, not appearance
 * - Touch targets ≥ 48dp on all import/replace CTAs and toggle switches
 * - Focus order: header → partition cards (top to bottom) → extraction CTA
 *
 * @param packageId UUID of the incremental package.
 * @param onNavigateBack Back navigation callback.
 * @param onNavigateToExtraction Forward to extraction when prerequisites satisfied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncrementalWizardScreen(
    packageId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExtraction: (String) -> Unit,
    viewModel: IncrementalWizardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Track which matchId is waiting for SAF result
    var pendingValidationMatchId by remember { mutableStateOf<String?>(null) }

    // SAF file picker launcher for base image import
    val baseImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val matchId = pendingValidationMatchId
        if (uri != null && matchId != null) {
            viewModel.validateBase(matchId, uri.toString())
        }
        pendingValidationMatchId = null
    }

    LaunchedEffect(packageId) {
        viewModel.initialize(packageId)
    }

    val colors = ForgeTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Prerequisites",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
    ) { paddingValues ->
        when (val state = uiState) {
            is IncrementalWizardUiState.Loading -> {
                LoadingContent(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            is IncrementalWizardUiState.Wizard -> {
                WizardContent(
                    data = state.data,
                    onImportBase = { matchId ->
                        pendingValidationMatchId = matchId
                        baseImagePicker.launch(arrayOf("*/*"))
                    },
                    onToggleRawExport = { matchId, allowed ->
                        viewModel.toggleRawExport(matchId, allowed)
                    },
                    onStartExtraction = {
                        onNavigateToExtraction(state.data.packageId)
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }

            is IncrementalWizardUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.initialize(packageId) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

// =============================================================================
// Wizard Content
// =============================================================================

@Composable
private fun WizardContent(
    data: WizardScreenData,
    onImportBase: (matchId: String) -> Unit,
    onToggleRawExport: (matchId: String, allowed: Boolean) -> Unit,
    onStartExtraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // =====================================================================
        // Header section
        // =====================================================================
        item(key = "header") {
            WizardHeader(data = data)
        }

        // =====================================================================
        // Cache summary
        // =====================================================================
        if (data.cachedMatchCount > 0) {
            item(key = "cache_summary") {
                CacheSummaryBanner(
                    cachedCount = data.cachedMatchCount,
                    totalCount = data.totalPrerequisiteCount,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // =====================================================================
        // Per-partition prerequisite cards
        // =====================================================================
        items(
            items = data.prerequisites,
            key = { it.matchId },
        ) { prerequisite ->
            PrerequisiteCard(
                prerequisite = prerequisite,
                isValidating = data.validatingMatchId == prerequisite.matchId,
                advancedModeEnabled = data.advancedModeEnabled,
                onImportBase = { onImportBase(prerequisite.matchId) },
                onToggleRawExport = { allowed ->
                    onToggleRawExport(prerequisite.matchId, allowed)
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // =====================================================================
        // Cache usage display
        // =====================================================================
        item(key = "cache_usage") {
            CacheUsageDisplay(
                usedFormatted = data.cacheUsedFormatted,
                ceilingFormatted = data.cacheCeilingFormatted,
                usedBytes = data.cacheUsedBytes,
                ceilingBytes = data.cacheCeilingBytes,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // =====================================================================
        // Extraction CTA
        // =====================================================================
        item(key = "cta") {
            ExtractionCta(
                allSatisfied = data.allSatisfied,
                unmatchedCount = data.unmatchedCount,
                onStartExtraction = onStartExtraction,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Bottom spacing for safe area
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun WizardHeader(
    data: WizardScreenData,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Column(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        // Tier badge + package name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SupportTierBadge(tier = data.supportTier)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Source fingerprint
        if (data.sourceFingerprint != null) {
            Text(
                text = "Required source build",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text(
                text = data.sourceFingerprint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textCode,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Target fingerprint
        if (data.targetFingerprint != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Target build",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text(
                text = data.targetFingerprint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prerequisite summary
        Text(
            text = "This incremental OTA requires base partition images from the source build. " +
                "Import base images for each partition below.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

// =============================================================================
// Cache summary banner
// =============================================================================

@Composable
private fun CacheSummaryBanner(
    cachedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.feedbackInfoSurface)
            .border(1.dp, colors.feedbackInfoBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null, // Decorative — cache status text follows
            tint = colors.feedbackInfoIcon,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$cachedCount of $totalCount bases found in cache",
            style = MaterialTheme.typography.bodySmall,
            color = colors.feedbackInfoText,
        )
    }
}

// =============================================================================
// Per-partition prerequisite card
// =============================================================================

/**
 * Prerequisite card for a single partition.
 *
 * Visual states per PRD State Matrix:
 * - MATCHED: green check, "Base validated" with truncated hash
 * - MISMATCHED: red X with field-level mismatch diff, "Replace Base" CTA
 * - MISSING: gray circle, "Base image required" with "Import Base" CTA
 */
@Composable
private fun PrerequisiteCard(
    prerequisite: PrerequisiteItem,
    isValidating: Boolean,
    advancedModeEnabled: Boolean,
    onImportBase: () -> Unit,
    onToggleRawExport: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    val (statusIcon, statusColor, statusLabel) = when {
        isValidating -> Triple(null, colors.textTertiary, "Validating…")
        prerequisite.status == "MATCHED" -> Triple(
            Icons.Default.CheckCircle,
            colors.feedbackSuccessIcon,
            "Base validated",
        )
        prerequisite.status == "MISMATCHED" -> Triple(
            Icons.Default.Error,
            colors.feedbackErrorIcon,
            "Mismatch",
        )
        else -> Triple(
            Icons.Default.RadioButtonUnchecked,
            colors.textDisabled,
            "Base image required",
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceDefault,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (prerequisite.status == "MISMATCHED") {
                    Modifier.border(1.dp, colors.feedbackErrorBorder, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = "${prerequisite.partitionName}: $statusLabel"
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Partition name + status icon row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Status icon
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.actionPrimaryBg,
                    )
                } else if (statusIcon != null) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prerequisite.partitionName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }

                // Import / Replace CTA
                if (prerequisite.status != "MATCHED" && !isValidating) {
                    OutlinedButton(
                        onClick = onImportBase,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.actionSecondaryText,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null, // Decorative — "Import"/"Replace" button text conveys meaning
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (prerequisite.status == "MISMATCHED") "Replace" else "Import",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Field-level mismatch diff — the key UX moment
            AnimatedVisibility(
                visible = prerequisite.status == "MISMATCHED" && prerequisite.mismatchDescription != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.feedbackErrorSurface)
                        .padding(12.dp),
                ) {
                    Text(
                        text = prerequisite.mismatchDescription ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.feedbackErrorText,
                    )
                }
            }

            // Matched hash display (truncated)
            if (prerequisite.status == "MATCHED" && prerequisite.requiredHash != null) {
                Text(
                    text = "SHA-256: ${prerequisite.requiredHash.take(16)}…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp, start = 36.dp),
                )
            }

            // Advanced mode: raw export toggle
            // PRD FR-5: "unsafe raw export allowed per partition with explicit red warning"
            AnimatedVisibility(
                visible = advancedModeEnabled && prerequisite.status != "MATCHED",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (prerequisite.rawExportAllowed) {
                                    colors.feedbackErrorSurface
                                } else {
                                    colors.surfaceRaised
                                },
                            )
                            .then(
                                if (prerequisite.rawExportAllowed) {
                                    Modifier.border(
                                        1.dp,
                                        colors.feedbackErrorBorder,
                                        RoundedCornerShape(8.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(12.dp),
                    ) {
                        if (prerequisite.rawExportAllowed) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = colors.feedbackErrorIcon,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow raw export (unverified)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (prerequisite.rawExportAllowed) {
                                    colors.feedbackErrorText
                                } else {
                                    colors.textSecondary
                                },
                                fontWeight = FontWeight.Medium,
                            )
                            if (prerequisite.rawExportAllowed) {
                                Text(
                                    text = "Output will be labeled raw_unverified. " +
                                        "Extraction may produce corrupt data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.feedbackErrorText,
                                )
                            }
                        }
                        Switch(
                            checked = prerequisite.rawExportAllowed,
                            onCheckedChange = onToggleRawExport,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.feedbackErrorIcon,
                                checkedThumbColor = colors.textOnAction,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Cache usage display
// =============================================================================

@Composable
private fun CacheUsageDisplay(
    usedFormatted: String,
    ceilingFormatted: String,
    usedBytes: Long,
    ceilingBytes: Long,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    val usagePercent = if (ceilingBytes > 0) {
        (usedBytes.toFloat() / ceilingBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceDefault)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Base image cache",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = "$usedFormatted / $ceilingFormatted",
                style = MaterialTheme.typography.labelMedium,
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
                        if (usagePercent > 0.9f) colors.feedbackErrorIcon
                        else colors.actionPrimaryBg,
                    ),
            )
        }
    }
}

// =============================================================================
// Extraction CTA
// =============================================================================

/**
 * Extraction CTA with disabled state tooltip.
 *
 * PRD FR-4: "Extraction CTA stays disabled until prerequisites validate per partition."
 * When disabled, shows specific tooltip explaining what's missing.
 */
@Composable
private fun ExtractionCta(
    allSatisfied: Boolean,
    unmatchedCount: Int,
    onStartExtraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onStartExtraction,
            enabled = allSatisfied,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics {
                    if (!allSatisfied) {
                        contentDescription = "Extract — disabled. " +
                            "$unmatchedCount partitions still require validated base images."
                    }
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.actionPrimaryBg,
                contentColor = colors.actionPrimaryText,
                disabledContainerColor = colors.actionPrimaryBgDisabled,
                disabledContentColor = colors.actionPrimaryTextDisabled,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (allSatisfied) "Start Extraction" else "Extract",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Disabled state tooltip
        AnimatedVisibility(
            visible = !allSatisfied,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = "$unmatchedCount partition(s) still require validated base images",
                style = MaterialTheme.typography.bodySmall,
                color = colors.feedbackWarningText,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// =============================================================================
// Loading / Error
// =============================================================================

@Composable
private fun LoadingContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = colors.actionPrimaryBg,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = colors.feedbackErrorIcon,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
