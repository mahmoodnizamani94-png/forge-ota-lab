package dev.forgeotalab.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.forgeotalab.contracts.model.ThemeMode
import dev.forgeotalab.ui.theme.ForgeTheme

/**
 * Settings screen — theme switching, diagnostics, adapter management, and advanced options.
 *
 * Accessibility:
 *   - Screen title "Settings" announced with heading semantics
 *   - All toggles have descriptive labels for TalkBack
 *   - Theme selector chips announce current selection
 *   - Touch targets ≥ 48dp
 *   - Back button has contentDescription "Navigate back"
 *
 * PRD surfaces mapped:
 *   - Theme toggle: "Dark mode as default. Light mode fully implemented as secondary."
 *   - Workspace cache: FR-6 workspace cleanup CTA
 *   - Crash reporting: PRD §Permissions, Policy, and Privacy — opt-in, default off
 *   - Privileged mode: SHOULD FR-1 — behind feature flag + warning dialog
 *   - Adapter info: FR-2 manifest version display and manual refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val colors = ForgeTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    var showPrivilegedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeFeedbackMessage()
        }
    }

    if (showPrivilegedDialog) {
        PrivilegedModeDialog(
            onConfirm = {
                viewModel.setPrivilegedModeEnabled(true)
                showPrivilegedDialog = false
            },
            onDismiss = {
                showPrivilegedDialog = false
            },
        )
    }

    Scaffold(
        containerColor = colors.surfacePage,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // =====================================================================
            // Appearance
            // =====================================================================
            SectionHeader(title = "Appearance")

            SettingsCard {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThemeSelector(
                    currentMode = state.themeMode,
                    onModeSelected = viewModel::setThemeMode,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================================
            // Storage
            // =====================================================================
            SectionHeader(title = "Storage")

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Workspace cache",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = formatBytes(state.workspaceCacheBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    TextButton(
                        onClick = viewModel::clearWorkspaceCache,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null, // Decorative — \"Clear\" text conveys meaning
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================================
            // Privacy
            // =====================================================================
            SectionHeader(title = "Privacy")

            SettingsCard {
                SettingsToggleRow(
                    label = "Crash reporting",
                    description = "Share anonymous crash reports to help improve stability",
                    checked = state.crashReportingEnabled,
                    onCheckedChange = viewModel::toggleCrashReporting,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================================
            // Advanced (conditionally visible)
            // =====================================================================
            if (state.privilegedModeVisible) {
                SectionHeader(title = "Advanced")

                SettingsCard {
                    SettingsToggleRow(
                        label = "Privileged mode",
                        description = "Enable Shizuku/root-based local partition capture",
                        checked = state.privilegedModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPrivilegedDialog = true
                            } else {
                                viewModel.setPrivilegedModeEnabled(false)
                            }
                        },
                        icon = Icons.Outlined.Security,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // =====================================================================
            // Adapter Management
            // =====================================================================
            SectionHeader(title = "Adapter Registry")

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Adapter manifest",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Check server for adapter updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    if (state.isRefreshingManifest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = colors.actionPrimaryBg,
                        )
                    } else {
                        IconButton(onClick = viewModel::refreshManifest) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh adapter manifest",
                                tint = colors.actionPrimaryBg,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================================
            // About
            // =====================================================================
            SectionHeader(title = "About")

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "App version",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = state.appVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// Component helpers — private to settings screen
// =============================================================================

@Composable
private fun SectionHeader(title: String) {
    val colors = ForgeTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.actionPrimaryBg,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit,
) {
    val colors = ForgeTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceRaised,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
) {
    val colors = ForgeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeChip(
            label = "Dark",
            icon = Icons.Outlined.DarkMode,
            selected = currentMode == ThemeMode.DARK,
            onClick = { onModeSelected(ThemeMode.DARK) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = "Light",
            icon = Icons.Outlined.LightMode,
            selected = currentMode == ThemeMode.LIGHT,
            onClick = { onModeSelected(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = "System",
            icon = Icons.Outlined.SettingsBrightness,
            selected = currentMode == ThemeMode.SYSTEM,
            onClick = { onModeSelected(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ForgeTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null, // Decorative — chip label text conveys meaning
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.actionPrimaryBg,
            selectedLabelColor = colors.textOnAction,
            selectedLeadingIconColor = colors.textOnAction,
            containerColor = colors.surfaceDefault,
            labelColor = colors.textSecondary,
            iconColor = colors.textSecondary,
        ),
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = ForgeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // Decorative — toggle label text conveys meaning
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.actionPrimaryBg,
                checkedTrackColor = colors.actionPrimaryBg.copy(alpha = 0.3f),
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceDefault,
            ),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
