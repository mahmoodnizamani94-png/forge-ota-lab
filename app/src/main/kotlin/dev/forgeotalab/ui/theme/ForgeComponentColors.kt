package dev.forgeotalab.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Forge component-level color tokens from prd/tokens/components.json.
 *
 * Every value references a [ForgeColors] semantic token — no direct primitive
 * references. This mirrors the JSON's design: component tokens are semantic
 * aliases only, so they automatically adapt to dark/light mode.
 *
 * Access via [ForgeTheme.components] in any Composable.
 */

// =============================================================================
// Sub-structures — one per component family
// =============================================================================

@Immutable
data class ForgeButtonColors(
    val primaryBg: Color,
    val primaryBgHover: Color,
    val primaryBgFocus: Color,
    val primaryBgActive: Color,
    val primaryBgLoading: Color,
    val primaryBgReadonly: Color,
    val primaryBgDisabled: Color,
    val primaryText: Color,
    val primaryTextDisabled: Color,
    val primaryFocusRing: Color,
    val secondaryBg: Color,
    val secondaryBgHover: Color,
    val secondaryBgActive: Color,
    val secondaryBgDisabled: Color,
    val secondaryBorder: Color,
    val secondaryText: Color,
    val secondaryFocusRing: Color,
    val ghostBg: Color,
    val ghostBgHover: Color,
    val ghostBgActive: Color,
    val ghostText: Color,
    val ghostFocusRing: Color,
    val destructiveBg: Color,
    val destructiveBgHover: Color,
    val destructiveBgDisabled: Color,
    val destructiveText: Color,
    val destructiveFocusRing: Color,
)

@Immutable
data class ForgeCardColors(
    val bg: Color,
    val bgHover: Color,
    val border: Color,
    val borderFocus: Color,
    val textTitle: Color,
    val textBody: Color,
    val shadow: Color,
    val shadowHover: Color,
)

@Immutable
data class ForgeInputColors(
    val bg: Color,
    val bgDisabled: Color,
    val border: Color,
    val borderFocus: Color,
    val borderError: Color,
    val borderDisabled: Color,
    val text: Color,
    val textPlaceholder: Color,
    val textDisabled: Color,
    val focusRing: Color,
)

@Immutable
data class ForgeAlertColors(
    val errorBg: Color,
    val errorBorder: Color,
    val errorText: Color,
    val errorIcon: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val warningIcon: Color,
    val successBg: Color,
    val successBorder: Color,
    val successText: Color,
    val successIcon: Color,
    val infoBg: Color,
    val infoBorder: Color,
    val infoText: Color,
    val infoIcon: Color,
)

@Immutable
data class ForgeNavColors(
    val bg: Color,
    val itemText: Color,
    val itemTextActive: Color,
    val itemIcon: Color,
    val itemIconActive: Color,
    val itemIndicator: Color,
    val borderTop: Color,
)

@Immutable
data class ForgeProgressColors(
    val trackBg: Color,
    val fill: Color,
    val fillSuccess: Color,
    val fillError: Color,
    val text: Color,
)

@Immutable
data class ForgeStatusColors(
    val verifiedIcon: Color,
    val verifiedText: Color,
    val failedIcon: Color,
    val failedText: Color,
    val skippedIcon: Color,
    val skippedText: Color,
    val pendingIcon: Color,
)

@Immutable
data class ForgeBadgeColors(
    val brandBg: Color,
    val brandText: Color,
    val successBg: Color,
    val successText: Color,
    val warningBg: Color,
    val warningText: Color,
)

@Immutable
data class ForgeCheckboxColors(
    val bgUnchecked: Color,
    val bgChecked: Color,
    val bgDisabled: Color,
    val borderUnchecked: Color,
    val borderFocus: Color,
    val checkIcon: Color,
    val checkIconDisabled: Color,
    val focusRing: Color,
)

@Immutable
data class ForgeTooltipColors(
    val bg: Color,
    val text: Color,
)

@Immutable
data class ForgeModalColors(
    val bg: Color,
    val scrim: Color,
)

@Immutable
data class ForgeSkeletonColors(
    val base: Color,
    val shimmer: Color,
)

// =============================================================================
// Aggregate component color holder
// =============================================================================

@Immutable
data class ForgeComponentColors(
    val button: ForgeButtonColors,
    val card: ForgeCardColors,
    val input: ForgeInputColors,
    val alert: ForgeAlertColors,
    val nav: ForgeNavColors,
    val progress: ForgeProgressColors,
    val status: ForgeStatusColors,
    val badge: ForgeBadgeColors,
    val checkbox: ForgeCheckboxColors,
    val tooltip: ForgeTooltipColors,
    val modal: ForgeModalColors,
    val skeleton: ForgeSkeletonColors,
)

// =============================================================================
// Factory — constructs component colors from semantic ForgeColors
// WHY a factory function: component tokens reference semantic tokens,
// not primitives. This function wires the reference chain so dark/light
// switching requires zero additional logic.
// =============================================================================

fun buildForgeComponentColors(colors: ForgeColors): ForgeComponentColors =
    ForgeComponentColors(
        button = ForgeButtonColors(
            primaryBg = colors.actionPrimaryBg,
            primaryBgHover = colors.actionPrimaryBgHover,
            primaryBgFocus = colors.actionPrimaryBgFocus,
            primaryBgActive = colors.actionPrimaryBgActive,
            primaryBgLoading = colors.actionPrimaryBgLoading,
            primaryBgReadonly = colors.actionPrimaryBgReadonly,
            primaryBgDisabled = colors.actionPrimaryBgDisabled,
            primaryText = colors.actionPrimaryText,
            primaryTextDisabled = colors.actionPrimaryTextDisabled,
            primaryFocusRing = colors.focusRing,
            secondaryBg = colors.actionSecondaryBg,
            secondaryBgHover = colors.actionSecondaryBgHover,
            secondaryBgActive = colors.actionSecondaryBgActive,
            secondaryBgDisabled = colors.actionSecondaryBgDisabled,
            secondaryBorder = colors.actionSecondaryBorder,
            secondaryText = colors.actionSecondaryText,
            secondaryFocusRing = colors.focusRing,
            ghostBg = Color.Transparent,
            ghostBgHover = colors.actionGhostBgHover,
            ghostBgActive = colors.actionGhostBgActive,
            ghostText = colors.textPrimary,
            ghostFocusRing = colors.focusRing,
            destructiveBg = colors.actionDestructiveBg,
            destructiveBgHover = colors.actionDestructiveBgHover,
            destructiveBgDisabled = colors.actionDestructiveBgDisabled,
            destructiveText = colors.actionDestructiveText,
            destructiveFocusRing = colors.focusRing,
        ),
        card = ForgeCardColors(
            bg = colors.surfaceDefault,
            bgHover = colors.surfaceRaised,
            border = colors.borderSubtle,
            borderFocus = colors.borderFocus,
            textTitle = colors.textPrimary,
            textBody = colors.textSecondary,
            shadow = colors.shadowSm,
            shadowHover = colors.shadowMd,
        ),
        input = ForgeInputColors(
            bg = colors.surfaceSunken,
            bgDisabled = colors.surfaceDefault,
            border = colors.borderDefault,
            borderFocus = colors.borderFocus,
            borderError = colors.borderError,
            borderDisabled = colors.borderDisabled,
            text = colors.textPrimary,
            textPlaceholder = colors.textTertiary,
            textDisabled = colors.textDisabled,
            focusRing = colors.focusRingInset,
        ),
        alert = ForgeAlertColors(
            errorBg = colors.feedbackErrorSurface,
            errorBorder = colors.feedbackErrorBorder,
            errorText = colors.feedbackErrorText,
            errorIcon = colors.feedbackErrorIcon,
            warningBg = colors.feedbackWarningSurface,
            warningBorder = colors.feedbackWarningBorder,
            warningText = colors.feedbackWarningText,
            warningIcon = colors.feedbackWarningIcon,
            successBg = colors.feedbackSuccessSurface,
            successBorder = colors.feedbackSuccessBorder,
            successText = colors.feedbackSuccessText,
            successIcon = colors.feedbackSuccessIcon,
            infoBg = colors.feedbackInfoSurface,
            infoBorder = colors.feedbackInfoBorder,
            infoText = colors.feedbackInfoText,
            infoIcon = colors.feedbackInfoIcon,
        ),
        nav = ForgeNavColors(
            bg = colors.surfacePage,
            itemText = colors.textTertiary,
            itemTextActive = colors.actionPrimaryBg,
            itemIcon = colors.textTertiary,
            itemIconActive = colors.actionPrimaryBg,
            itemIndicator = colors.actionPrimaryBg,
            borderTop = colors.borderSubtle,
        ),
        progress = ForgeProgressColors(
            trackBg = colors.surfaceSunken,
            fill = colors.actionPrimaryBg,
            fillSuccess = colors.feedbackSuccessIcon,
            fillError = colors.feedbackErrorIcon,
            text = colors.textSecondary,
        ),
        status = ForgeStatusColors(
            verifiedIcon = colors.feedbackSuccessIcon,
            verifiedText = colors.feedbackSuccessText,
            failedIcon = colors.feedbackErrorIcon,
            failedText = colors.feedbackErrorText,
            skippedIcon = colors.textTertiary,
            skippedText = colors.textTertiary,
            pendingIcon = colors.textSecondary,
        ),
        badge = ForgeBadgeColors(
            brandBg = colors.surfaceEmphasis,
            brandText = colors.textOnEmphasis,
            successBg = colors.feedbackSuccessSurface,
            successText = colors.feedbackSuccessText,
            warningBg = colors.feedbackWarningSurface,
            warningText = colors.feedbackWarningText,
        ),
        checkbox = ForgeCheckboxColors(
            bgUnchecked = colors.surfaceSunken,
            bgChecked = colors.actionPrimaryBg,
            bgDisabled = colors.actionPrimaryBgDisabled,
            borderUnchecked = colors.borderDefault,
            borderFocus = colors.borderFocus,
            checkIcon = colors.textOnAction,
            checkIconDisabled = colors.textDisabled,
            focusRing = colors.focusRing,
        ),
        tooltip = ForgeTooltipColors(
            bg = colors.surfaceInverse,
            text = colors.textInversePrimary,
        ),
        modal = ForgeModalColors(
            bg = colors.surfaceOverlay,
            scrim = colors.overlayScrim,
        ),
        skeleton = ForgeSkeletonColors(
            base = colors.skeletonBase,
            shimmer = colors.skeletonShimmer,
        ),
    )

/**
 * CompositionLocal for Forge component-level colors.
 *
 * Access via [ForgeTheme.components] in any Composable.
 * Default is built from dark theme colors.
 */
val LocalForgeComponents = staticCompositionLocalOf {
    buildForgeComponentColors(DarkForgeColors)
}
