package dev.forgeotalab.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH → sRGB color conversion for the Forge design system.
 *
 * WHY in-project instead of a library: The PRD mandates no external design
 * system libraries. This is a well-documented, tested pure function following
 * the CSS Color Level 4 specification.
 *
 * Pipeline:
 * ```
 * OKLCH(L, C, H)
 *   → OKLAB(L, a = C·cos(H·π/180), b = C·sin(H·π/180))
 *   → Linear sRGB via LMS intermediate (3×3 matrices from Björn Ottosson)
 *   → Gamma sRGB via transfer function: x ≤ 0.0031308 → 12.92·x, else 1.055·x^(1/2.4) − 0.055
 *   → Compose Color(r, g, b, alpha) with clamping to [0, 1]
 * ```
 *
 * Reference: https://bottosson.github.io/posts/oklab/
 * CSS Color 4 matrices: https://www.w3.org/TR/css-color-4/#color-conversion-code
 *
 * @param l Perceived lightness in [0, 1]. 0 = black, 1 = white.
 * @param c Chroma (colorfulness). Typically [0, ~0.4]. 0 = achromatic.
 * @param h Hue angle in degrees [0, 360).
 * @param alpha Opacity in [0, 1]. Default = 1 (fully opaque).
 * @return Compose [Color] in sRGB. Out-of-gamut values are clamped.
 */
fun oklch(l: Double, c: Double, h: Double, alpha: Double = 1.0): Color {
    // Step 1: OKLCH → OKLAB
    val hRad = h * Math.PI / 180.0
    val labL = l
    val labA = c * cos(hRad)
    val labB = c * sin(hRad)

    // Step 2: OKLAB → LMS (cube root domain)
    // Inverse of Björn Ottosson's OKLAB→LMS matrix
    val lmsL = labL + 0.3963377774 * labA + 0.2158037573 * labB
    val lmsM = labL - 0.1055613458 * labA - 0.0638541728 * labB
    val lmsS = labL - 0.0894841775 * labA - 1.2914855480 * labB

    // Step 3: Un-cube the LMS values (they were cube-rooted in the forward transform)
    val l3 = lmsL * lmsL * lmsL
    val m3 = lmsM * lmsM * lmsM
    val s3 = lmsS * lmsS * lmsS

    // Step 4: LMS → Linear sRGB
    // Matrix from CSS Color Level 4 spec (LMS to linear-sRGB)
    val linearR = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val linearG = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val linearB = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

    // Step 5: Linear sRGB → Gamma sRGB (IEC 61966-2-1 transfer function)
    val r = linearToGamma(linearR)
    val g = linearToGamma(linearG)
    val b = linearToGamma(linearB)

    // Step 6: Clamp to sRGB gamut [0, 1] and construct Compose Color
    // WHY clamp instead of gamut mapping: follows browser CSS behavior for
    // out-of-gamut OKLCH values. The token files have already been designed
    // with sRGB-safe chroma values (see primitives.json notes on "sRGB risk").
    return Color(
        red = r.coerceIn(0.0, 1.0).toFloat(),
        green = g.coerceIn(0.0, 1.0).toFloat(),
        blue = b.coerceIn(0.0, 1.0).toFloat(),
        alpha = alpha.coerceIn(0.0, 1.0).toFloat(),
    )
}

/**
 * IEC 61966-2-1 (sRGB) transfer function: linear → gamma-encoded.
 *
 * The piecewise function avoids numerical issues near zero:
 * - Linear segment for small values (≤ 0.0031308)
 * - Power curve with gamma ≈ 2.4 for the rest
 */
private fun linearToGamma(value: Double): Double =
    if (value <= 0.0031308) {
        12.92 * value
    } else {
        1.055 * value.pow(1.0 / 2.4) - 0.055
    }
