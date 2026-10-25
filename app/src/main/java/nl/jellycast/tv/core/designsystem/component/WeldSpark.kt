package nl.jellycast.tv.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors

/** Length of the cooling tail behind a weld spark. */
const val HOT_TAIL_DP = 52f

/** From the trail (transparent purple) via lavender to white-hot at the head. */
val HotTail =
    listOf(
        JellyCastColors.NeonPurple.copy(alpha = 0f),
        Color(0x8CC8AAFF),
        Color(0xD9F2F6FF),
    )

private const val FLICKER_MIN = 0.85f
private const val FLICKER_RANGE = 0.25f
private const val FLICKER_MIN_INTERVAL_MS = 30
private const val FLICKER_INTERVAL_SPREAD_MS = 50
private const val NS_PER_MS = 1_000_000L

/** Irregular brightness like an arc weld: a new value every 30–80 ms. */
class Flicker {
    private var value = 1f
    private var nextNs = 0L

    fun next(nowNs: Long): Float {
        if (nowNs >= nextNs) {
            value = FLICKER_MIN + Random.nextFloat() * FLICKER_RANGE
            nextNs =
                nowNs +
                (FLICKER_MIN_INTERVAL_MS + Random.nextInt(FLICKER_INTERVAL_SPREAD_MS)) * NS_PER_MS
        }
        return value
    }
}

private const val HOT_TAIL_GLOW_ALPHA = 0.25f
private const val HOT_TAIL_GLOW_WIDTH_DP = 5f
private const val HOT_TAIL_WIDTH_DP = 2.4f

/** A wide faint stroke as glow and the tail line itself; no blur. */
internal fun DrawScope.drawHotTail(path: Path, brush: Brush, alpha: Float) {
    drawPath(
        path,
        brush,
        alpha = HOT_TAIL_GLOW_ALPHA * alpha,
        style = Stroke(HOT_TAIL_GLOW_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path,
        brush,
        alpha = alpha,
        style = Stroke(HOT_TAIL_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round),
    )
}

private val WeldPurple =
    Brush.radialGradient(
        listOf(Color(0xFFC8AAFF), Color(0x009B6BFF)),
        center = Offset.Zero,
        radius = 1f,
    )
private val WeldBlue =
    Brush.radialGradient(
        listOf(Color(0xFFDCEBFF), Color(0x007FC8FF)),
        center = Offset.Zero,
        radius = 1f,
    )
private val WeldCore =
    Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.8f),
        0.55f to Color(0xB3F2F6FF),
        1f to Color(0x00C8DCFF),
        center = Offset.Zero,
        radius = 1f,
    )

private const val ALONG_GLOW_RADIUS_DP = 9f
private const val ALONG_GLOW_STRETCH = 2.6f
private const val ALONG_GLOW_ALPHA = 0.28f
private const val ACROSS_GLOW_RADIUS_DP = 5f
private const val ACROSS_GLOW_STRETCH = 3.2f
private const val ACROSS_GLOW_ALPHA = 0.32f
private const val CORE_RADIUS_DP = 3.2f
private const val CORE_STRETCH = 1.8f

/**
 * A small white-hot point stretched along [angle], with a purple glow along the line and a light
 * blue one across it: three small gradients, no blur. [size] 1 is the size on a card; [intensity]
 * is brightness × flicker (0–1.1).
 */
fun DrawScope.drawWeldPoint(position: Offset, angle: Float, size: Float, intensity: Float) {
    if (intensity <= 0f) return
    val dp = density
    translate(position.x, position.y) {
        rotateRad(angle, pivot = Offset.Zero) {
            val alongRadius = ALONG_GLOW_RADIUS_DP * dp * size * intensity
            scale(ALONG_GLOW_STRETCH * alongRadius, alongRadius, pivot = Offset.Zero) {
                drawCircle(
                    WeldPurple,
                    radius = 1f,
                    center = Offset.Zero,
                    alpha = ALONG_GLOW_ALPHA * intensity,
                )
            }
            val acrossRadius = ACROSS_GLOW_RADIUS_DP * dp * size * intensity
            scale(acrossRadius, ACROSS_GLOW_STRETCH * acrossRadius, pivot = Offset.Zero) {
                drawCircle(
                    WeldBlue,
                    radius = 1f,
                    center = Offset.Zero,
                    alpha = ACROSS_GLOW_ALPHA * intensity,
                )
            }
            val coreRadius = CORE_RADIUS_DP * dp * size
            scale(CORE_STRETCH * coreRadius, coreRadius, pivot = Offset.Zero) {
                drawCircle(
                    WeldCore,
                    radius = 1f,
                    center = Offset.Zero,
                    alpha = intensity.coerceIn(0f, 1f),
                )
            }
        }
    }
}
