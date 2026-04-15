package dev.forgeotalab.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Forge primitive color palette — raw OKLCH scales from prd/tokens/primitives.json.
 *
 * Every value calls [oklch] (defined in OklchColor.kt). No hardcoded hex.
 * Token file structure preserved: 6 hue families × 10 lightness stops (50–900).
 *
 * Hue strategy (split-complementary):
 *   Brand:   H = 38°  (warm copper) — primary identity
 *   Neutral: H = 5°   (warm grays)  — surfaces and text
 *   Error:   H = 22°  (red)         — destructive / failure
 *   Warning: H = 70°  (amber)       — caution / advisory
 *   Success: H = 148° (green)       — verified / complete
 *   Accent:  H = 195° (cool teal)   — info / links / focus rings
 */
object ForgePalette {

    // =========================================================================
    // Brand — Warm copper, H = 38°
    // Anchor: L = 0.58, C = 0.14 (stop 500)
    // =========================================================================
    val Brand50: Color = oklch(0.97, 0.017, 38.0)
    val Brand100: Color = oklch(0.93, 0.031, 38.0)
    val Brand200: Color = oklch(0.88, 0.053, 38.0)
    val Brand300: Color = oklch(0.79, 0.081, 38.0)
    val Brand400: Color = oklch(0.71, 0.112, 38.0)
    val Brand500: Color = oklch(0.58, 0.140, 38.0)
    val Brand600: Color = oklch(0.51, 0.133, 38.0)
    val Brand700: Color = oklch(0.41, 0.123, 38.0)
    val Brand800: Color = oklch(0.31, 0.109, 38.0)
    val Brand900: Color = oklch(0.19, 0.084, 38.0)

    // =========================================================================
    // Neutral — Warm grays, H = 5° (brand H × 0.15), C ≤ 0.012
    // Never pure gray — always carries a hint of warmth.
    // =========================================================================
    val Neutral50: Color = oklch(0.97, 0.003, 5.0)
    val Neutral100: Color = oklch(0.93, 0.004, 5.0)
    val Neutral200: Color = oklch(0.87, 0.005, 5.0)
    val Neutral300: Color = oklch(0.78, 0.007, 5.0)
    val Neutral400: Color = oklch(0.70, 0.008, 5.0)
    val Neutral500: Color = oklch(0.58, 0.010, 5.0)
    val Neutral600: Color = oklch(0.50, 0.009, 5.0)
    val Neutral700: Color = oklch(0.40, 0.008, 5.0)
    val Neutral800: Color = oklch(0.30, 0.007, 5.0)
    val Neutral900: Color = oklch(0.18, 0.005, 5.0)

    // =========================================================================
    // Error — Red family, H = 22°
    // Anchor: L = 0.58, C = 0.18 (stop 500)
    // Chroma reduced at 600–700 stops per sRGB risk heuristic.
    // =========================================================================
    val Error50: Color = oklch(0.97, 0.022, 22.0)
    val Error100: Color = oklch(0.93, 0.040, 22.0)
    val Error200: Color = oklch(0.88, 0.068, 22.0)
    val Error300: Color = oklch(0.80, 0.104, 22.0)
    val Error400: Color = oklch(0.72, 0.144, 22.0)
    val Error500: Color = oklch(0.58, 0.180, 22.0)
    val Error600: Color = oklch(0.52, 0.150, 22.0) // C reduced from 0.171 — sRGB risk
    val Error700: Color = oklch(0.42, 0.140, 22.0) // C reduced from 0.158 — sRGB risk
    val Error800: Color = oklch(0.32, 0.140, 22.0)
    val Error900: Color = oklch(0.20, 0.108, 22.0)

    // =========================================================================
    // Warning — Amber family, H = 70°
    // Anchor: L = 0.60, C = 0.14 (stop 500)
    // =========================================================================
    val Warning50: Color = oklch(0.97, 0.017, 70.0)
    val Warning100: Color = oklch(0.94, 0.031, 70.0)
    val Warning200: Color = oklch(0.89, 0.053, 70.0)
    val Warning300: Color = oklch(0.80, 0.081, 70.0)
    val Warning400: Color = oklch(0.72, 0.112, 70.0)
    val Warning500: Color = oklch(0.60, 0.140, 70.0)
    val Warning600: Color = oklch(0.52, 0.133, 70.0)
    val Warning700: Color = oklch(0.42, 0.123, 70.0)
    val Warning800: Color = oklch(0.32, 0.109, 70.0)
    val Warning900: Color = oklch(0.20, 0.084, 70.0)

    // =========================================================================
    // Success — Green family, H = 148°
    // Anchor: L = 0.60, C = 0.16 (stop 500)
    // =========================================================================
    val Success50: Color = oklch(0.97, 0.019, 148.0)
    val Success100: Color = oklch(0.93, 0.035, 148.0)
    val Success200: Color = oklch(0.88, 0.061, 148.0)
    val Success300: Color = oklch(0.80, 0.093, 148.0)
    val Success400: Color = oklch(0.72, 0.128, 148.0)
    val Success500: Color = oklch(0.60, 0.160, 148.0)
    val Success600: Color = oklch(0.52, 0.152, 148.0)
    val Success700: Color = oklch(0.42, 0.141, 148.0)
    val Success800: Color = oklch(0.32, 0.125, 148.0)
    val Success900: Color = oklch(0.20, 0.096, 148.0)

    // =========================================================================
    // Accent — Cool teal info/accent, H = 195°
    // Split-complementary to brand (ΔH ≈ 157°). Anchor C = 0.12.
    // =========================================================================
    val Accent50: Color = oklch(0.97, 0.014, 195.0)
    val Accent100: Color = oklch(0.93, 0.026, 195.0)
    val Accent200: Color = oklch(0.88, 0.046, 195.0)
    val Accent300: Color = oklch(0.80, 0.070, 195.0)
    val Accent400: Color = oklch(0.72, 0.096, 195.0)
    val Accent500: Color = oklch(0.60, 0.120, 195.0)
    val Accent600: Color = oklch(0.52, 0.114, 195.0)
    val Accent700: Color = oklch(0.42, 0.106, 195.0)
    val Accent800: Color = oklch(0.32, 0.094, 195.0)
    val Accent900: Color = oklch(0.20, 0.072, 195.0)
}
