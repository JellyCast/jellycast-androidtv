package nl.jellycast.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme

/** All coordinates below are in a 200 × 200 view box. */
private const val VIEWBOX = 200f
private const val LINE_WIDTH = 7f
private const val WELD_TAIL_LENGTH = 37f

/** Fraction of the line drawn before the signal starts to fade in. */
private const val SIGNAL_START = 0.6f
private val SignalCenter = Offset(100f, 138f)
private const val SIGNAL_DOT_RADIUS = 6f
private const val SIGNAL_LINE_WIDTH = 6f
private val SignalArcRadii = floatArrayOf(18f, 32f)

private const val WELD_GLOW_RADIUS = 14f
private val WeldCoreSize = Size(14f, 6f)

private val Lavender = Color(0xFFD9CCFF)

/**
 * The Cast triangle in neon, drawn in code (the same drawing as the TV app's). [drawProgress]
 * (0–1) draws the line in, led by the weld spark; it is read only in the draw phase.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, drawProgress: () -> Float = { 1f }) {
    Box(
        modifier =
            modifier.drawWithCache {
                val outline = trianglePath()
                val measure = PathMeasure().apply { setPath(outline, false) }
                val segment = Path()
                val neon =
                    Brush.linearGradient(
                        0f to JellyCastColors.NeonBlue,
                        0.4f to JellyCastColors.NeonWhite,
                        0.75f to JellyCastColors.NeonPurple,
                        1f to JellyCastColors.NeonPink,
                        start = Offset(30f, 30f),
                        end = Offset(170f, 165f),
                    )
                onDrawBehind {
                    val progress = drawProgress().coerceIn(0f, 1f)
                    val unit = size.minDimension / VIEWBOX
                    scale(unit, unit, pivot = Offset.Zero) {
                        val head = measure.length * progress
                        segment.reset()
                        measure.getSegment(0f, head, segment, true)
                        drawGlowingStroke(segment, neon, width = LINE_WIDTH)

                        val signalAlpha =
                            ((progress - SIGNAL_START) / (1f - SIGNAL_START)).coerceIn(0f, 1f)
                        if (signalAlpha > 0f) drawSignal(neon, signalAlpha)

                        if (progress > 0f) {
                            drawWeld(
                                head = measure.getPosition(head),
                                tailStart =
                                    measure.getPosition((head - WELD_TAIL_LENGTH).coerceAtLeast(0f)),
                                tangent = measure.getTangent(head),
                            )
                        }
                    }
                }
            },
    )
}

/** Starts at the weld spark on the right edge and runs counter-clockwise. */
private fun trianglePath(): Path = Path().apply {
    moveTo(134f, 91.6f)
    lineTo(100f, 30f)
    lineTo(28f, 160f)
    lineTo(172f, 160f)
    close()
}

private fun DrawScope.drawGlowingStroke(path: Path, brush: Brush, width: Float) {
    drawPath(path, brush, alpha = 0.06f, style = roundStroke(20f))
    drawPath(path, brush, alpha = 0.13f, style = roundStroke(13f))
    drawPath(path, brush, style = roundStroke(width))
}

private fun DrawScope.drawSignal(brush: Brush, alpha: Float) {
    drawCircle(brush, radius = SIGNAL_DOT_RADIUS, center = SignalCenter, alpha = alpha)
    for (radius in SignalArcRadii) {
        drawArc(
            brush = brush,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(SignalCenter.x - radius, SignalCenter.y - radius),
            size = Size(radius * 2, radius * 2),
            alpha = alpha,
            style = roundStroke(SIGNAL_LINE_WIDTH),
        )
    }
}

private fun DrawScope.drawWeld(head: Offset, tailStart: Offset, tangent: Offset) {
    if (head != tailStart) {
        drawLine(
            brush =
                Brush.linearGradient(
                    0f to JellyCastColors.Accent.copy(alpha = 0f),
                    0.6f to Lavender,
                    1f to Color.White,
                    start = tailStart,
                    end = head,
                ),
            start = tailStart,
            end = head,
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to JellyCastColors.NeonPurple.copy(alpha = 0.7f),
                1f to JellyCastColors.NeonPurple.copy(alpha = 0f),
                center = head,
                radius = WELD_GLOW_RADIUS,
            ),
        radius = WELD_GLOW_RADIUS,
        center = head,
    )
    rotateRad(atan2(tangent.y, tangent.x), pivot = head) {
        drawOval(Color.White, topLeft = head - WeldCoreSize.center, size = WeldCoreSize)
    }
}

private fun roundStroke(width: Float) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** "Jelly" bold in text, "Cast" light in the accent. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, size: TextUnit = 36.sp) {
    val name = stringResource(R.string.app_name)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = JellyCastColors.Text)) {
                append(stringResource(R.string.settings_brand_first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = JellyCastColors.Accent)) {
                append(stringResource(R.string.settings_brand_second))
            }
        },
        style = TextStyle(fontSize = size, letterSpacing = WORDMARK_TRACKING.em),
        modifier = modifier.semantics { contentDescription = name },
    )
}

private const val WORDMARK_TRACKING = -0.02f

@Preview
@Composable
private fun BrandMarkPreview() {
    JellyCastTheme {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(Modifier.size(132.dp))
            BrandMark(Modifier.size(132.dp), drawProgress = { 0.5f })
            BrandWordmark()
        }
    }
}
