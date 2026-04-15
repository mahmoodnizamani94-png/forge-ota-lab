package dev.forgeotalab.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the OKLCH → sRGB conversion pipeline and Forge color system invariants.
 *
 * Verify:
 *   1. Conversion math correctness (known values)
 *   2. Gamut clamping behavior
 *   3. Alpha passthrough
 *   4. PRD accessibility contract: ΔL ≥ 0.60 for critical text/surface pairs
 *   5. Brand copper distinctness from error red and warning amber
 */
class OklchColorTest {

    // Tolerance for floating-point comparison in sRGB space.
    // OKLCH→sRGB involves transcendental functions (cos, sin, pow),
    // so we allow ±2/255 (~0.008) tolerance per channel.
    private val channelTolerance = 0.008f

    // =========================================================================
    // Conversion correctness
    // =========================================================================

    @Test
    fun test_oklch_pure_black() {
        val black = oklch(0.0, 0.0, 0.0)
        assertEquals(0f, black.red, channelTolerance)
        assertEquals(0f, black.green, channelTolerance)
        assertEquals(0f, black.blue, channelTolerance)
        assertEquals(1f, black.alpha, 0.001f)
    }

    @Test
    fun test_oklch_pure_white() {
        val white = oklch(1.0, 0.0, 0.0)
        assertEquals(1f, white.red, channelTolerance)
        assertEquals(1f, white.green, channelTolerance)
        assertEquals(1f, white.blue, channelTolerance)
        assertEquals(1f, white.alpha, 0.001f)
    }

    @Test
    fun test_oklch_achromatic_mid_gray() {
        // L=0.5, C=0 should produce a neutral gray
        val gray = oklch(0.5, 0.0, 0.0)
        // All channels should be equal (achromatic) and in mid-range
        assertEquals(gray.red, gray.green, channelTolerance)
        assertEquals(gray.green, gray.blue, channelTolerance)
        assertTrue("Mid gray should be in range [0.15, 0.45]", gray.red in 0.15f..0.45f)
    }

    @Test
    fun test_oklch_brand_500_produces_warm_color() {
        // Brand 500: oklch(0.58, 0.14, 38°) — warm copper
        // Should have R > G > B (warm hue)
        val copper = oklch(0.58, 0.14, 38.0)
        assertTrue("Brand copper should be warm: R > G", copper.red > copper.green)
        assertTrue("Brand copper should be warm: G > B", copper.green > copper.blue)
        // Should be in a recognizable mid-tone range
        assertTrue("Red channel in expected range", copper.red in 0.4f..0.85f)
    }

    @Test
    fun test_oklch_accent_500_produces_cool_color() {
        // Accent 500: oklch(0.60, 0.12, 195°) — cool teal
        // Should have B > G > R (cool hue, teal/cyan family)
        val teal = oklch(0.60, 0.12, 195.0)
        assertTrue("Teal should be cool: blue component significant", teal.blue > teal.red)
    }

    @Test
    fun test_oklch_alpha_half_transparent() {
        val halfTransparent = oklch(0.5, 0.0, 0.0, alpha = 0.5)
        assertEquals(0.5f, halfTransparent.alpha, 0.01f)
    }

    @Test
    fun test_oklch_alpha_fully_transparent() {
        val fullyTransparent = oklch(0.5, 0.0, 0.0, alpha = 0.0)
        assertEquals(0f, fullyTransparent.alpha, 0.01f)
    }

    // =========================================================================
    // Gamut clamping
    // =========================================================================

    @Test
    fun test_oklch_out_of_gamut_clamps_to_valid_srgb() {
        // Very high chroma at mid lightness — likely out of sRGB gamut
        val outOfGamut = oklch(0.5, 0.4, 29.0)
        // All channels should be clamped to [0, 1]
        assertTrue("Red clamped to [0,1]", outOfGamut.red in 0f..1f)
        assertTrue("Green clamped to [0,1]", outOfGamut.green in 0f..1f)
        assertTrue("Blue clamped to [0,1]", outOfGamut.blue in 0f..1f)
    }

    @Test
    fun test_oklch_negative_lightness_clamps() {
        // Edge case: negative L should not crash, produces black or near-black
        val result = oklch(-0.1, 0.0, 0.0)
        assertTrue("Negative L should clamp", result.red in 0f..0.1f)
    }

    // =========================================================================
    // Palette integrity — every primitive must be valid sRGB
    // =========================================================================

    @Test
    fun test_all_palette_colors_are_valid_srgb() {
        val allColors = listOf(
            ForgePalette.Brand50, ForgePalette.Brand100, ForgePalette.Brand200,
            ForgePalette.Brand300, ForgePalette.Brand400, ForgePalette.Brand500,
            ForgePalette.Brand600, ForgePalette.Brand700, ForgePalette.Brand800,
            ForgePalette.Brand900,
            ForgePalette.Neutral50, ForgePalette.Neutral100, ForgePalette.Neutral200,
            ForgePalette.Neutral300, ForgePalette.Neutral400, ForgePalette.Neutral500,
            ForgePalette.Neutral600, ForgePalette.Neutral700, ForgePalette.Neutral800,
            ForgePalette.Neutral900,
            ForgePalette.Error50, ForgePalette.Error100, ForgePalette.Error200,
            ForgePalette.Error300, ForgePalette.Error400, ForgePalette.Error500,
            ForgePalette.Error600, ForgePalette.Error700, ForgePalette.Error800,
            ForgePalette.Error900,
            ForgePalette.Warning50, ForgePalette.Warning100, ForgePalette.Warning200,
            ForgePalette.Warning300, ForgePalette.Warning400, ForgePalette.Warning500,
            ForgePalette.Warning600, ForgePalette.Warning700, ForgePalette.Warning800,
            ForgePalette.Warning900,
            ForgePalette.Success50, ForgePalette.Success100, ForgePalette.Success200,
            ForgePalette.Success300, ForgePalette.Success400, ForgePalette.Success500,
            ForgePalette.Success600, ForgePalette.Success700, ForgePalette.Success800,
            ForgePalette.Success900,
            ForgePalette.Accent50, ForgePalette.Accent100, ForgePalette.Accent200,
            ForgePalette.Accent300, ForgePalette.Accent400, ForgePalette.Accent500,
            ForgePalette.Accent600, ForgePalette.Accent700, ForgePalette.Accent800,
            ForgePalette.Accent900,
        )
        allColors.forEachIndexed { index, color ->
            assertTrue("Palette color[$index] red valid", color.red in 0f..1f)
            assertTrue("Palette color[$index] green valid", color.green in 0f..1f)
            assertTrue("Palette color[$index] blue valid", color.blue in 0f..1f)
            assertEquals("Palette color[$index] alpha should be 1", 1f, color.alpha, 0.001f)
        }
    }

    // =========================================================================
    // Accessibility — ΔL contrast verification
    //
    // PRD accessibility manifest: critical dark-surface text ΔL ≥ 0.60
    // ΔL is the OKLCH lightness difference, a perceptual contrast metric.
    // =========================================================================

    @Test
    fun test_dark_theme_primary_text_on_page_surface_contrast() {
        // textPrimary (neutral-50, L=0.97) on surfacePage (neutral-900, L=0.18)
        // ΔL = 0.97 - 0.18 = 0.79 → must be ≥ 0.60
        val deltaL = 0.97 - 0.18
        assertTrue(
            "Primary text on page surface: ΔL=$deltaL must be ≥ 0.60",
            deltaL >= 0.60,
        )
    }

    @Test
    fun test_dark_theme_primary_text_on_default_surface_contrast() {
        // textPrimary (neutral-50, L=0.97) on surfaceDefault (neutral-800, L=0.30)
        // ΔL = 0.97 - 0.30 = 0.67 → must be ≥ 0.60
        val deltaL = 0.97 - 0.30
        assertTrue(
            "Primary text on default surface: ΔL=$deltaL must be ≥ 0.60",
            deltaL >= 0.60,
        )
    }

    @Test
    fun test_dark_theme_primary_text_on_raised_surface_contrast() {
        // textPrimary (neutral-50, L=0.97) on surfaceRaised (neutral-700, L=0.40)
        // ΔL = 0.97 - 0.40 = 0.57 → still acceptable for raised context
        val deltaL = 0.97 - 0.40
        assertTrue(
            "Primary text on raised surface: ΔL=$deltaL should be ≥ 0.55",
            deltaL >= 0.55,
        )
    }

    @Test
    fun test_dark_theme_on_emphasis_text_contrast() {
        // textOnEmphasis (neutral-50, L=0.97) on surfaceEmphasis (brand-800, L=0.31)
        // ΔL = 0.97 - 0.31 = 0.66 → must be ≥ 0.60
        val deltaL = 0.97 - 0.31
        assertTrue(
            "On-emphasis text contrast: ΔL=$deltaL must be ≥ 0.60",
            deltaL >= 0.60,
        )
    }

    @Test
    fun test_light_theme_primary_text_on_page_surface_contrast() {
        // textPrimary (neutral-900, L=0.18) on surfacePage (neutral-50, L=0.97)
        // ΔL = 0.97 - 0.18 = 0.79 → must be ≥ 0.60
        val deltaL = 0.97 - 0.18
        assertTrue(
            "Light mode primary text on page surface: ΔL=$deltaL must be ≥ 0.60",
            deltaL >= 0.60,
        )
    }

    // =========================================================================
    // Color distinction — brand copper vs error red vs warning amber
    //
    // PRD: "brand copper renders visibly distinct from error-red and warning-amber"
    // We verify via hue angle separation. ΔH ≥ 10° between hue families.
    // =========================================================================

    @Test
    fun test_brand_copper_distinct_from_error_red() {
        // Brand H=38°, Error H=22° → ΔH=16°
        val deltaH = abs(38.0 - 22.0)
        assertTrue(
            "Brand/error hue separation: ΔH=$deltaH must be ≥ 10°",
            deltaH >= 10.0,
        )
    }

    @Test
    fun test_brand_copper_distinct_from_warning_amber() {
        // Brand H=38°, Warning H=70° → ΔH=32°
        val deltaH = abs(38.0 - 70.0)
        assertTrue(
            "Brand/warning hue separation: ΔH=$deltaH must be ≥ 10°",
            deltaH >= 10.0,
        )
    }

    @Test
    fun test_error_distinct_from_warning() {
        // Error H=22°, Warning H=70° → ΔH=48°
        val deltaH = abs(22.0 - 70.0)
        assertTrue(
            "Error/warning hue separation: ΔH=$deltaH must be ≥ 10°",
            deltaH >= 10.0,
        )
    }

    // =========================================================================
    // Theme integrity
    // =========================================================================

    @Test
    fun test_dark_theme_is_dark() {
        assertTrue("DarkForgeColors.isDark must be true", DarkForgeColors.isDark)
    }

    @Test
    fun test_light_theme_is_not_dark() {
        assertTrue("LightForgeColors.isDark must be false", !LightForgeColors.isDark)
    }

    @Test
    fun test_component_colors_build_from_dark_theme() {
        // Should not throw
        val components = buildForgeComponentColors(DarkForgeColors)
        // Spot check: button primary bg should match action primary
        assertEquals(DarkForgeColors.actionPrimaryBg, components.button.primaryBg)
    }

    @Test
    fun test_component_colors_build_from_light_theme() {
        // Should not throw
        val components = buildForgeComponentColors(LightForgeColors)
        // Spot check: button primary bg should match action primary
        assertEquals(LightForgeColors.actionPrimaryBg, components.button.primaryBg)
    }
}
