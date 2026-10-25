package nl.jellycast.tv.core.designsystem.component

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors

/** Fraction of the outline covered by the comet trail behind its head. */
private const val TRAIL_FRACTION = 0.45f

/** The hot tail never covers more than this fraction of the outline on small elements. */
private const val MAX_HOT_TAIL_FRACTION = 0.25f

private const val BORDER_WIDTH_DP = 2f

/** Width (dp) and alpha: two glow strokes and the trail line itself. */
private val GlowPasses = listOf(8f to 0.06f, 5f to 0.13f, 2.2f to 1f)

/**
 * Position 1.0 of the sweep is the comet head. The trail runs from transparent via light blue and
 * white to purple and pink, so it reads as one long tail with a gradient.
 */
private val TrailStops = floatArrayOf(0f, 0.55f, 0.7f, 0.84f, 0.95f, 1f)
private val TrailColors =
    intArrayOf(
        Color.Transparent.toArgb(),
        JellyCastColors.NeonBlue.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonBlue.copy(alpha = 0.5f).toArgb(),
        JellyCastColors.NeonWhite.copy(alpha = 0.85f).toArgb(),
        JellyCastColors.NeonPurple.toArgb(),
        JellyCastColors.NeonPink.toArgb(),
    )

/** White with a hint of blue and purple: the selected border under the comet. */
private val SelectedRing =
    listOf(
        JellyCastColors.NeonWhite.copy(alpha = 0.6f),
        JellyCastColors.NeonBlue.copy(alpha = 0.5f),
        JellyCastColors.NeonWhite.copy(alpha = 0.6f),
        Color(0x80C8AAFF),
        JellyCastColors.NeonWhite.copy(alpha = 0.6f),
    )

/** The full neon gradient, shown as a static border before the comet starts. */
private val NeonStatic =
    listOf(
        JellyCastColors.NeonBlue,
        JellyCastColors.NeonWhite,
        JellyCastColors.NeonPurple,
        JellyCastColors.NeonPink,
        JellyCastColors.NeonBlue,
    )

/** How far the halo reaches outside the card; stays within the LazyRow clip. */
private const val HALO_EXTENT_DP = 12f
private const val DEGREES_PER_TURN = 360f

private const val HALO_LINE_WIDTH_DP = 1.5f
private const val HALO_LINE_ALPHA = 0.8f

/**
 * Two lamps behind the card: each sweep holds two colored lights with dark gaps between them.
 * The sweeps turn in opposite directions at different speeds, so the lights cross and mix and
 * the glow keeps shining from another place in another color.
 */
private val HaloStops = floatArrayOf(0f, 0.18f, 0.36f, 0.55f, 0.72f, 0.9f, 1f)
private val HaloColorsA =
    intArrayOf(
        JellyCastColors.NeonBlue.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonBlue.toArgb(),
        JellyCastColors.NeonPurple.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonPink.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonPink.toArgb(),
        JellyCastColors.NeonPurple.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonBlue.copy(alpha = 0f).toArgb(),
    )
private val HaloColorsB =
    intArrayOf(
        JellyCastColors.NeonPurple.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonPurple.toArgb(),
        JellyCastColors.NeonPurple.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonWhite.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonWhite.copy(alpha = 0.8f).toArgb(),
        JellyCastColors.NeonBlue.copy(alpha = 0f).toArgb(),
        JellyCastColors.NeonPurple.copy(alpha = 0f).toArgb(),
    )

/** Position and direction (radians, 0 = to the right) of the comet head on the outline. */
internal class CometHead {
    var position = Offset.Zero
    var angle = 0f
}

/**
 * The element outline as a path, measured once per size. Per frame only a segment is cut out of
 * it (`getSegment`), and one sweep gradient rotates along with the head. Nothing is allocated for
 * the outline, trail or sweep while drawing.
 */
internal class NeonRing {
    private val outline = Path()
    private val trail = Path()
    private val hot = Path()
    private val measure = PathMeasure()
    private var length = 0f
    private var preparedFor = Size.Unspecified
    private val head = CometHead()
    private val matrix = Matrix()
    private var sweep: SweepGradient? = null
    private val sweepBrush =
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = requireNotNull(sweep)
        }
    private var staticBrush: Brush = Brush.sweepGradient(NeonStatic)
    private var selectedBrush: Brush = Brush.sweepGradient(SelectedRing)
    private val haloA = RotatingSweep()
    private val haloB = RotatingSweep()
    private val haloGlow = HaloGlow()

    fun reset() {
        preparedFor = Size.Unspecified
    }

    fun prepare(scope: DrawScope, shape: Shape) {
        if (scope.size == preparedFor) return
        preparedFor = scope.size
        outline.reset()
        val shapeOutline = shape.createOutline(scope.size, scope.layoutDirection, scope)
        outline.addOutline(shapeOutline)
        measure.setPath(outline, true)
        length = measure.length
        val c = scope.center
        sweep = SweepGradient(c.x, c.y, TrailColors, TrailStops)
        haloA.prepare(c, HaloColorsA, HaloStops)
        haloB.prepare(c, HaloColorsB, HaloStops)
        haloGlow.prepare(scope.size, shapeOutline, with(scope) { HALO_EXTENT_DP.dp.toPx() })
        staticBrush = Brush.sweepGradient(NeonStatic, c)
        selectedBrush = Brush.sweepGradient(SelectedRing, c)
    }

    fun drawBase(scope: DrawScope, staticAlpha: Float) = with(scope) {
        drawPath(outline, selectedBrush, style = Stroke(BORDER_WIDTH_DP.dp.toPx()))
        if (staticAlpha > 0f) {
            drawPath(
                outline,
                staticBrush,
                alpha = staticAlpha,
                style = Stroke(BORDER_WIDTH_DP.dp.toPx()),
            )
        }
    }

    /** The soft colored light behind the card; draw it before the content. */
    fun drawHalo(scope: DrawScope, degreesA: Float, degreesB: Float, fade: Float) = with(scope) {
        haloA.rotate(degreesA)
        haloB.rotate(degreesB)
        haloGlow.update(degreesA / DEGREES_PER_TURN, degreesB / DEGREES_PER_TURN, fade)
        val extent = HALO_EXTENT_DP.dp.toPx()
        drawRect(
            haloGlow.brush,
            topLeft = Offset(-extent, -extent),
            size = Size(size.width + 2 * extent, size.height + 2 * extent),
        )
    }

    /** The same moving colors as a thin line on the border, over the content. */
    fun drawHaloLine(scope: DrawScope, fade: Float) = with(scope) {
        val stroke = Stroke(HALO_LINE_WIDTH_DP.dp.toPx())
        drawPath(outline, haloA.brush, alpha = HALO_LINE_ALPHA * fade, style = stroke)
        drawPath(outline, haloB.brush, alpha = HALO_LINE_ALPHA * fade, style = stroke)
    }

    /** Draws the trail and its hot tail; returns the head. */
    fun drawComet(scope: DrawScope, progress: Float, fade: Float): CometHead = with(scope) {
        val headDistance = progress * length
        cutSegment(trail, from = headDistance - length * TRAIL_FRACTION, to = headDistance)
        head.position = measure.getPosition(headDistance)
        val tangent = measure.getTangent(headDistance)
        head.angle = atan2(tangent.y, tangent.x)
        rotateSweepToHead()

        for ((width, alpha) in GlowPasses) {
            drawPath(
                trail,
                sweepBrush,
                alpha = alpha * fade,
                style = Stroke(width.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        val hotLength = minOf(HOT_TAIL_DP.dp.toPx(), length * MAX_HOT_TAIL_FRACTION)
        val hotStart = headDistance - hotLength
        cutSegment(hot, from = hotStart, to = headDistance)
        val tailBrush =
            Brush.linearGradient(
                HotTail,
                start = measure.getPosition(if (hotStart < 0f) length + hotStart else hotStart),
                end = head.position,
            )
        drawHotTail(hot, tailBrush, fade)
        head
    }

    /** Cuts [from]..[to] out of the closed outline into [into], wrapping past the start. */
    private fun cutSegment(into: Path, from: Float, to: Float) {
        into.reset()
        if (from < 0f) {
            measure.getSegment(length + from, length, into, true)
            measure.getSegment(0f, to, into, true)
        } else {
            measure.getSegment(from, to, into, true)
        }
    }

    private fun DrawScope.rotateSweepToHead() {
        val c = center
        val headAngleDeg = Math.toDegrees(
            atan2(head.position.y - c.y, head.position.x - c.x).toDouble(),
        ).toFloat()
        matrix.setRotate(headAngleDeg, c.x, c.y)
        requireNotNull(sweep).setLocalMatrix(matrix)
    }
}

/** A sweep gradient around the element center that turns without allocating per frame. */
private class RotatingSweep {
    private var center = Offset.Zero
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    val brush =
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = requireNotNull(shader)
        }

    fun prepare(center: Offset, colors: IntArray, stops: FloatArray) {
        this.center = center
        shader = SweepGradient(center.x, center.y, colors, stops)
    }

    fun rotate(degrees: Float) {
        matrix.setRotate(degrees, center.x, center.y)
        requireNotNull(shader).setLocalMatrix(matrix)
    }
}
