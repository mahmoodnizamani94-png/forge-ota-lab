package dev.forgeotalab.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material 3 color schemes mapped from Forge semantic tokens.
 *
 * WHY both Material 3 schemes AND ForgeColors: Material 3 components
 * (buttons, cards, dialogs, etc.) require a MaterialTheme.colorScheme.
 * ForgeColors via LocalForgeColors provides the full Forge palette
 * for custom components that need more colors than M3 exposes.
 *
 * WHY dynamic color is OFF: "Forge identity is the identity, not the
 * device wallpaper." — PRD, Design Identity section.
 *
 * M3 Role Mapping Rationale:
 *   primary       → brand copper (action CTA)
 *   secondary     → secondary intent (outlined actions)
 *   tertiary      → teal accent (info, links)
 *   surface*      → warm neutrals at varying elevation
 *   error         → red feedback icon color
 */
private fun buildDarkColorScheme(colors: ForgeColors) = darkColorScheme(
    primary = colors.actionPrimaryBg,
    onPrimary = colors.textOnAction,
    primaryContainer = colors.surfaceEmphasis,
    onPrimaryContainer = colors.textOnEmphasis,
    secondary = colors.actionSecondaryBorder,
    onSecondary = colors.actionSecondaryText,
    secondaryContainer = colors.surfaceRaised,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.textLink,
    onTertiary = colors.textOnAction,
    tertiaryContainer = colors.feedbackInfoSurface,
    onTertiaryContainer = colors.feedbackInfoText,
    background = colors.surfacePage,
    onBackground = colors.textPrimary,
    surface = colors.surfaceDefault,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceRaised,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.actionPrimaryBg,
    inverseSurface = colors.surfaceInverse,
    inverseOnSurface = colors.textInversePrimary,
    inversePrimary = colors.actionPrimaryBg,
    error = colors.feedbackErrorIcon,
    onError = colors.textOnAction,
    errorContainer = colors.feedbackErrorSurface,
    onErrorContainer = colors.feedbackErrorText,
    outline = colors.borderDefault,
    outlineVariant = colors.borderSubtle,
    scrim = colors.overlayScrim,
    surfaceBright = colors.surfaceRaised,
    surfaceDim = colors.surfaceSunken,
    surfaceContainer = colors.surfaceDefault,
    surfaceContainerHigh = colors.surfaceRaised,
    surfaceContainerHighest = colors.surfaceOverlay,
    surfaceContainerLow = colors.surfacePage,
    surfaceContainerLowest = colors.surfaceSunken,
)

private fun buildLightColorScheme(colors: ForgeColors) = lightColorScheme(
    primary = colors.actionPrimaryBg,
    onPrimary = colors.textOnAction,
    primaryContainer = colors.surfaceEmphasis,
    onPrimaryContainer = colors.textOnEmphasis,
    secondary = colors.actionSecondaryBorder,
    onSecondary = colors.actionSecondaryText,
    secondaryContainer = colors.surfaceRaised,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.textLink,
    onTertiary = colors.textOnAction,
    tertiaryContainer = colors.feedbackInfoSurface,
    onTertiaryContainer = colors.feedbackInfoText,
    background = colors.surfacePage,
    onBackground = colors.textPrimary,
    surface = colors.surfaceDefault,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceRaised,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.actionPrimaryBg,
    inverseSurface = colors.surfaceInverse,
    inverseOnSurface = colors.textInversePrimary,
    inversePrimary = colors.actionPrimaryBg,
    error = colors.feedbackErrorIcon,
    onError = colors.textOnAction,
    errorContainer = colors.feedbackErrorSurface,
    onErrorContainer = colors.feedbackErrorText,
    outline = colors.borderDefault,
    outlineVariant = colors.borderSubtle,
    scrim = colors.overlayScrim,
    surfaceBright = colors.surfaceRaised,
    surfaceDim = colors.surfaceSunken,
    surfaceContainer = colors.surfaceDefault,
    surfaceContainerHigh = colors.surfaceRaised,
    surfaceContainerHighest = colors.surfaceOverlay,
    surfaceContainerLow = colors.surfacePage,
    surfaceContainerLowest = colors.surfaceSunken,
)

/**
 * Forge OTA Lab theme.
 *
 * PRD: "Dark mode as default. Light mode fully implemented as secondary option."
 * When [useDarkTheme] is not explicitly set, follows system preference.
 *
 * Provides three composition locals:
 *   - [LocalForgeColors] — semantic tokens (surfaces, text, actions, feedback)
 *   - [LocalForgeComponents] — component-level tokens (button, card, alert, etc.)
 *   - [MaterialTheme] colorScheme — M3 component integration
 */
@Composable
fun ForgeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val forgeColors = if (useDarkTheme) DarkForgeColors else LightForgeColors
    val forgeComponents = buildForgeComponentColors(forgeColors)
    val colorScheme = if (useDarkTheme) {
        buildDarkColorScheme(forgeColors)
    } else {
        buildLightColorScheme(forgeColors)
    }

    // Set status bar appearance to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !useDarkTheme
        }
    }

    CompositionLocalProvider(
        LocalForgeColors provides forgeColors,
        LocalForgeComponents provides forgeComponents,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ForgeTypography,
            content = content,
        )
    }
}

/**
 * Convenience accessor for Forge-specific tokens in Composables.
 *
 * Usage:
 *   `ForgeTheme.colors.surfacePage` — semantic color tokens
 *   `ForgeTheme.components.button.primaryBg` — component color tokens
 */
object ForgeTheme {
    val colors: ForgeColors
        @Composable
        get() = LocalForgeColors.current

    val components: ForgeComponentColors
        @Composable
        get() = LocalForgeComponents.current
}
