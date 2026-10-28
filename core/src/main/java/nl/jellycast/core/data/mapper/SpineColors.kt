package nl.jellycast.core.data.mapper

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val BASE83 =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

/**
 * Average color (0xRRGGBB) of a BlurHash: characters 2–5 are the DC component, which is exactly the
 * image's average color. A spine color thus costs no request and no bitmap analysis on the device.
 */
fun blurHashAverageColor(hash: String?): Int? {
    if (hash == null || hash.length < 6) return null
    var value = 0
    for (char in hash.substring(2, 6)) {
        val digit = BASE83.indexOf(char)
        if (digit < 0) return null
        value = value * 83 + digit
    }
    return value
}

/** [darkArgb] is the dark variant of the hue, even when the spine itself is light. */
data class SpineColor(val argb: Long, val isLight: Boolean, val darkArgb: Long = argb)

// A dark logo gets a light spine; on a dark spine it would disappear.
fun spineColor(posterBlurHash: String?, logoBlurHash: String?): SpineColor {
    val poster = blurHashAverageColor(posterBlurHash) ?: DEFAULT_SPINE_RGB
    val logoLightness = blurHashAverageColor(logoBlurHash)?.let { rgbToHsl(it)[2] }
    val isLight = logoLightness != null && logoLightness < DARK_LOGO_LIGHTNESS
    val (h, s, _) = rgbToHsl(poster)
    val rgb = hslToRgb(h, s, if (isLight) LIGHT_SPINE_LIGHTNESS else DARK_SPINE_LIGHTNESS)
    val dark = hslToRgb(h, s, DARK_SPINE_LIGHTNESS)
    return SpineColor(
        argb = 0xFF000000L or rgb.toLong(),
        isLight = isLight,
        darkArgb = 0xFF000000L or dark.toLong(),
    )
}

private const val DEFAULT_SPINE_RGB = 0x2A2A2E
private const val DARK_LOGO_LIGHTNESS = 0.38f
private const val DARK_SPINE_LIGHTNESS = 0.15f
private const val LIGHT_SPINE_LIGHTNESS = 0.80f

/** Returns hue in degrees (0–360), saturation and lightness in 0–1. */
internal fun rgbToHsl(rgb: Int): FloatArray {
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2
    val d = maxC - minC
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val s = d / (1 - abs(2 * l - 1))
    val h =
        when (maxC) {
            r -> 60 * (((g - b) / d).mod(6f))
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
    return floatArrayOf(h, s, l)
}

internal fun hslToRgb(h: Float, s: Float, l: Float): Int {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60).mod(2f) - 1))
    val m = l - c / 2
    val (r, g, b) =
        when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    fun channel(v: Float) = ((v + m) * 255 + 0.5f).toInt().coerceIn(0, 255)
    return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}
