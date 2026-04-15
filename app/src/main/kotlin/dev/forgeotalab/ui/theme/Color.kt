package dev.forgeotalab.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Forge semantic color system — every token from prd/tokens/semantic.json.
 *
 * WHY not Material You dynamic color: The PRD explicitly states
 * "Dynamic color (Material You) OFF. Forge identity is the identity."
 *
 * All values trace to either:
 *   - [ForgePalette] references (for tokens like "{color.neutral.900}")
 *   - Inline [oklch] calls (for tokens with unique OKLCH values)
 *
 * Zero hardcoded hex. Every color flows through the OKLCH→sRGB pipeline.
 *
 * Naming convention: JSON path "color.surface.page" → Kotlin `surfacePage`.
 * Dashes become camelCase: "bg-hover" → `bgHover`, "on-action" → `onAction`.
 */

@Immutable
data class ForgeColors(

    // =========================================================================
    // Surface — Background layers for the obsidian workbench metaphor
    // =========================================================================
    val surfacePage: Color,
    val surfaceDefault: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceSunken: Color,
    val surfaceEmphasis: Color,
    val surfaceInverse: Color,

    // =========================================================================
    // Text — Typographic hierarchy on surfaces
    // =========================================================================
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textOnAction: Color,
    val textOnEmphasis: Color,
    val textLink: Color,
    val textLinkVisited: Color,
    val textCode: Color,
    val textInversePrimary: Color,
    val textInverseSecondary: Color,

    // =========================================================================
    // Action: Primary — Brand copper CTAs
    // =========================================================================
    val actionPrimaryBg: Color,
    val actionPrimaryBgHover: Color,
    val actionPrimaryBgActive: Color,
    val actionPrimaryBgFocus: Color,
    val actionPrimaryBgLoading: Color,
    val actionPrimaryBgReadonly: Color,
    val actionPrimaryBgDisabled: Color,
    val actionPrimaryText: Color,
    val actionPrimaryTextDisabled: Color,

    // =========================================================================
    // Action: Secondary — Outlined / bordered buttons
    // =========================================================================
    val actionSecondaryBg: Color,
    val actionSecondaryBgHover: Color,
    val actionSecondaryBgActive: Color,
    val actionSecondaryBgDisabled: Color,
    val actionSecondaryBorder: Color,
    val actionSecondaryText: Color,

    // =========================================================================
    // Action: Ghost — Minimal chrome buttons
    // =========================================================================
    val actionGhostBgHover: Color,
    val actionGhostBgActive: Color,

    // =========================================================================
    // Action: Destructive — Danger zone
    // =========================================================================
    val actionDestructiveBg: Color,
    val actionDestructiveBgHover: Color,
    val actionDestructiveBgDisabled: Color,
    val actionDestructiveText: Color,

    // =========================================================================
    // Feedback — Semantic status families (error, warning, success, info)
    // Each family has surface, border, text, and icon treatments
    // =========================================================================
    val feedbackErrorSurface: Color,
    val feedbackErrorBorder: Color,
    val feedbackErrorText: Color,
    val feedbackErrorIcon: Color,

    val feedbackWarningSurface: Color,
    val feedbackWarningBorder: Color,
    val feedbackWarningText: Color,
    val feedbackWarningIcon: Color,

    val feedbackSuccessSurface: Color,
    val feedbackSuccessBorder: Color,
    val feedbackSuccessText: Color,
    val feedbackSuccessIcon: Color,

    val feedbackInfoSurface: Color,
    val feedbackInfoBorder: Color,
    val feedbackInfoText: Color,
    val feedbackInfoIcon: Color,

    // =========================================================================
    // Border — Stroke treatments
    // =========================================================================
    val borderDefault: Color,
    val borderStrong: Color,
    val borderSubtle: Color,
    val borderFocus: Color,
    val borderDisabled: Color,
    val borderError: Color,
    val borderSuccess: Color,

    // =========================================================================
    // Focus — Accessibility ring indicators
    // =========================================================================
    val focusRing: Color,
    val focusRingInset: Color,

    // =========================================================================
    // Selection — Text/element selection highlights
    // =========================================================================
    val selectionBg: Color,
    val selectionText: Color,

    // =========================================================================
    // Overlay — Scrim and hover treatments
    // =========================================================================
    val overlayScrim: Color,
    val overlayHover: Color,

    // =========================================================================
    // Skeleton — Loading placeholder animation
    // =========================================================================
    val skeletonBase: Color,
    val skeletonShimmer: Color,

    // =========================================================================
    // Shadow — Elevation hierarchy (alpha-varied)
    // =========================================================================
    val shadowSm: Color,
    val shadowMd: Color,
    val shadowLg: Color,

    // =========================================================================
    // Theme mode flag
    // =========================================================================
    val isDark: Boolean,
)

// =============================================================================
// Dark theme — default. Obsidian surfaces, high-lightness text.
// Source: semantic.json → "dark" object
// =============================================================================
val DarkForgeColors = ForgeColors(
    // Surface
    surfacePage = ForgePalette.Neutral900,
    surfaceDefault = ForgePalette.Neutral800,
    surfaceRaised = ForgePalette.Neutral700,
    surfaceOverlay = ForgePalette.Neutral700,
    surfaceSunken = oklch(0.13, 0.004, 5.0),
    surfaceEmphasis = ForgePalette.Brand800,
    surfaceInverse = ForgePalette.Neutral100,

    // Text
    textPrimary = ForgePalette.Neutral50,
    textSecondary = ForgePalette.Neutral400,
    textTertiary = ForgePalette.Neutral500,
    textDisabled = ForgePalette.Neutral600,
    textOnAction = ForgePalette.Neutral50,
    textOnEmphasis = ForgePalette.Neutral50,
    textLink = ForgePalette.Accent400,
    textLinkVisited = ForgePalette.Accent500,
    textCode = ForgePalette.Brand300,
    textInversePrimary = ForgePalette.Neutral900,
    textInverseSecondary = ForgePalette.Neutral700,

    // Action: Primary
    actionPrimaryBg = ForgePalette.Brand500,
    actionPrimaryBgHover = ForgePalette.Brand400,
    actionPrimaryBgActive = ForgePalette.Brand600,
    actionPrimaryBgFocus = ForgePalette.Brand500,
    actionPrimaryBgLoading = ForgePalette.Brand500,
    actionPrimaryBgReadonly = ForgePalette.Brand700,
    actionPrimaryBgDisabled = ForgePalette.Neutral700,
    actionPrimaryText = ForgePalette.Neutral50,
    actionPrimaryTextDisabled = ForgePalette.Neutral500,

    // Action: Secondary
    actionSecondaryBg = oklch(0.18, 0.005, 5.0, alpha = 0.0),
    actionSecondaryBgHover = ForgePalette.Neutral700,
    actionSecondaryBgActive = ForgePalette.Neutral600,
    actionSecondaryBgDisabled = oklch(0.18, 0.005, 5.0, alpha = 0.0),
    actionSecondaryBorder = ForgePalette.Neutral600,
    actionSecondaryText = ForgePalette.Neutral100,

    // Action: Ghost
    actionGhostBgHover = oklch(0.40, 0.008, 5.0, alpha = 0.4),
    actionGhostBgActive = oklch(0.50, 0.009, 5.0, alpha = 0.5),

    // Action: Destructive
    actionDestructiveBg = ForgePalette.Error500,
    actionDestructiveBgHover = ForgePalette.Error400,
    actionDestructiveBgDisabled = ForgePalette.Neutral700,
    actionDestructiveText = ForgePalette.Neutral50,

    // Feedback: Error
    feedbackErrorSurface = oklch(0.20, 0.05, 22.0),
    feedbackErrorBorder = oklch(0.48, 0.14, 22.0),
    feedbackErrorText = oklch(0.82, 0.10, 22.0),
    feedbackErrorIcon = oklch(0.72, 0.16, 22.0),

    // Feedback: Warning
    feedbackWarningSurface = oklch(0.20, 0.05, 70.0),
    feedbackWarningBorder = oklch(0.50, 0.13, 70.0),
    feedbackWarningText = oklch(0.82, 0.10, 70.0),
    feedbackWarningIcon = oklch(0.72, 0.14, 70.0),

    // Feedback: Success
    feedbackSuccessSurface = oklch(0.20, 0.05, 148.0),
    feedbackSuccessBorder = oklch(0.48, 0.14, 148.0),
    feedbackSuccessText = oklch(0.82, 0.10, 148.0),
    feedbackSuccessIcon = oklch(0.72, 0.16, 148.0),

    // Feedback: Info
    feedbackInfoSurface = oklch(0.20, 0.04, 195.0),
    feedbackInfoBorder = oklch(0.48, 0.12, 195.0),
    feedbackInfoText = oklch(0.82, 0.08, 195.0),
    feedbackInfoIcon = oklch(0.72, 0.12, 195.0),

    // Border
    borderDefault = oklch(0.40, 0.006, 5.0),
    borderStrong = oklch(0.55, 0.008, 5.0),
    borderSubtle = oklch(0.30, 0.004, 5.0),
    borderFocus = ForgePalette.Accent400,
    borderDisabled = oklch(0.30, 0.003, 5.0),
    borderError = oklch(0.48, 0.14, 22.0),
    borderSuccess = oklch(0.48, 0.14, 148.0),

    // Focus
    focusRing = ForgePalette.Accent400,
    focusRingInset = ForgePalette.Accent400,

    // Selection
    selectionBg = oklch(0.31, 0.109, 38.0, alpha = 0.3),
    selectionText = ForgePalette.Neutral50,

    // Overlay
    overlayScrim = oklch(0.05, 0.002, 5.0, alpha = 0.7),
    overlayHover = oklch(0.97, 0.003, 5.0, alpha = 0.06),

    // Skeleton
    skeletonBase = oklch(0.25, 0.004, 5.0),
    skeletonShimmer = oklch(0.35, 0.006, 5.0),

    // Shadow
    shadowSm = oklch(0.05, 0.002, 5.0, alpha = 0.3),
    shadowMd = oklch(0.05, 0.002, 5.0, alpha = 0.5),
    shadowLg = oklch(0.05, 0.002, 5.0, alpha = 0.7),

    isDark = true,
)

// =============================================================================
// Light theme — secondary. Near-white surfaces per PRD (not pure white).
// Source: semantic.json → "light" object
// =============================================================================
val LightForgeColors = ForgeColors(
    // Surface — note: default/raised/overlay are oklch(1.00, 0, 0) per token file
    surfacePage = ForgePalette.Neutral50,
    surfaceDefault = oklch(1.00, 0.0, 0.0),
    surfaceRaised = oklch(1.00, 0.0, 0.0),
    surfaceOverlay = oklch(1.00, 0.0, 0.0),
    surfaceSunken = ForgePalette.Neutral100,
    surfaceEmphasis = ForgePalette.Brand50,
    surfaceInverse = ForgePalette.Neutral800,

    // Text
    textPrimary = ForgePalette.Neutral900,
    textSecondary = ForgePalette.Neutral600,
    textTertiary = ForgePalette.Neutral500,
    textDisabled = ForgePalette.Neutral400,
    textOnAction = ForgePalette.Neutral50,
    textOnEmphasis = ForgePalette.Neutral900,
    textLink = ForgePalette.Accent600,
    textLinkVisited = ForgePalette.Accent700,
    textCode = ForgePalette.Brand700,
    textInversePrimary = ForgePalette.Neutral50,
    textInverseSecondary = ForgePalette.Neutral200,

    // Action: Primary
    actionPrimaryBg = ForgePalette.Brand500,
    actionPrimaryBgHover = ForgePalette.Brand600,
    actionPrimaryBgActive = oklch(0.48, 0.126, 38.0),
    actionPrimaryBgFocus = ForgePalette.Brand500,
    actionPrimaryBgLoading = ForgePalette.Brand500,
    actionPrimaryBgReadonly = ForgePalette.Brand400,
    actionPrimaryBgDisabled = ForgePalette.Neutral200,
    actionPrimaryText = ForgePalette.Neutral50,
    actionPrimaryTextDisabled = ForgePalette.Neutral400,

    // Action: Secondary
    actionSecondaryBg = oklch(0.97, 0.003, 5.0, alpha = 0.0),
    actionSecondaryBgHover = ForgePalette.Neutral100,
    actionSecondaryBgActive = ForgePalette.Neutral200,
    actionSecondaryBgDisabled = oklch(0.97, 0.003, 5.0, alpha = 0.0),
    actionSecondaryBorder = ForgePalette.Neutral400,
    actionSecondaryText = ForgePalette.Neutral900,

    // Action: Ghost
    actionGhostBgHover = oklch(0.18, 0.005, 5.0, alpha = 0.06),
    actionGhostBgActive = oklch(0.18, 0.005, 5.0, alpha = 0.12),

    // Action: Destructive
    actionDestructiveBg = ForgePalette.Error500,
    actionDestructiveBgHover = ForgePalette.Error600,
    actionDestructiveBgDisabled = ForgePalette.Neutral200,
    actionDestructiveText = ForgePalette.Neutral50,

    // Feedback: Error
    feedbackErrorSurface = oklch(0.95, 0.03, 22.0),
    feedbackErrorBorder = ForgePalette.Error500,
    feedbackErrorText = ForgePalette.Error700,
    feedbackErrorIcon = ForgePalette.Error600,

    // Feedback: Warning
    feedbackWarningSurface = oklch(0.95, 0.03, 70.0),
    feedbackWarningBorder = ForgePalette.Warning500,
    feedbackWarningText = ForgePalette.Warning700,
    feedbackWarningIcon = ForgePalette.Warning600,

    // Feedback: Success
    feedbackSuccessSurface = oklch(0.95, 0.03, 148.0),
    feedbackSuccessBorder = ForgePalette.Success500,
    feedbackSuccessText = ForgePalette.Success700,
    feedbackSuccessIcon = ForgePalette.Success600,

    // Feedback: Info
    feedbackInfoSurface = oklch(0.95, 0.02, 195.0),
    feedbackInfoBorder = ForgePalette.Accent500,
    feedbackInfoText = ForgePalette.Accent700,
    feedbackInfoIcon = ForgePalette.Accent600,

    // Border
    borderDefault = ForgePalette.Neutral300,
    borderStrong = ForgePalette.Neutral500,
    borderSubtle = ForgePalette.Neutral200,
    borderFocus = ForgePalette.Accent500,
    borderDisabled = ForgePalette.Neutral200,
    borderError = ForgePalette.Error500,
    borderSuccess = ForgePalette.Success500,

    // Focus
    focusRing = ForgePalette.Accent500,
    focusRingInset = ForgePalette.Accent500,

    // Selection
    selectionBg = oklch(0.88, 0.053, 38.0, alpha = 0.3),
    selectionText = ForgePalette.Neutral900,

    // Overlay
    overlayScrim = oklch(0.05, 0.002, 5.0, alpha = 0.5),
    overlayHover = oklch(0.18, 0.005, 5.0, alpha = 0.04),

    // Skeleton
    skeletonBase = ForgePalette.Neutral200,
    skeletonShimmer = ForgePalette.Neutral100,

    // Shadow
    shadowSm = oklch(0.18, 0.005, 5.0, alpha = 0.08),
    shadowMd = oklch(0.18, 0.005, 5.0, alpha = 0.14),
    shadowLg = oklch(0.18, 0.005, 5.0, alpha = 0.22),

    isDark = false,
)

/**
 * CompositionLocal for Forge semantic colors.
 *
 * Access via [ForgeTheme.colors] in any Composable.
 * Default is dark theme — matching the PRD's "dark mode default" requirement.
 */
val LocalForgeColors = staticCompositionLocalOf { DarkForgeColors }
