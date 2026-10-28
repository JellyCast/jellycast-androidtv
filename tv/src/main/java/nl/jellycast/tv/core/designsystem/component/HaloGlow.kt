package nl.jellycast.tv.core.designsystem.component

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors

/**
 * The glow fades with the distance to the outline per pixel. Stacked strokes left visible bands at
 * every stroke edge; the dither hides the 8-bit steps of a faint color on the dark background.
 */
private const val HALO_SHADER =
    """
    uniform float2 center;
    uniform float2 halfSize;
    uniform float radius;
    uniform float extent;
    uniform float turnA;
    uniform float turnB;
    uniform float strength;
    layout(color) uniform half4 lightA1;
    layout(color) uniform half4 lightA2;
    layout(color) uniform half4 lightB1;
    layout(color) uniform half4 lightB2;

    const float TAU = 6.2831853;
    const float LIGHT_1 = 0.18;
    const float LIGHT_2 = 0.72;
    const float LIGHT_HALF_WIDTH = 0.18;
    const float FALLOFF = 1.3;

    float distanceToOutline(float2 p) {
        float2 q = abs(p) - halfSize + radius;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }

    float light(float turn, float at) {
        float d = abs(fract(turn - at + 0.5) - 0.5);
        return max(0.0, 1.0 - d / LIGHT_HALF_WIDTH);
    }

    half4 lamp(float turn, half4 first, half4 second) {
        half4 a = half4(first.rgb * first.a, first.a) * light(turn, LIGHT_1);
        half4 b = half4(second.rgb * second.a, second.a) * light(turn, LIGHT_2);
        return a + b;
    }

    half4 main(float2 xy) {
        float2 p = xy - center;
        float t = clamp(distanceToOutline(p) / extent, 0.0, 1.0);
        float glow = strength * pow(1.0 - t, FALLOFF);
        float turn = fract(atan(p.y, p.x) / TAU + 1.0);
        half4 a = lamp(turn - turnA, lightA1, lightA2);
        half4 b = lamp(turn - turnB, lightB1, lightB2);
        half4 c = (a + b * (1.0 - a.a)) * glow;
        float noise = fract(52.9829189 * fract(dot(xy, float2(0.06711056, 0.00583715))));
        float dither = (noise - 0.5) / 255.0;
        c.a = clamp(c.a + dither, 0.0, 1.0);
        c.rgb = clamp(c.rgb + dither, 0.0, c.a);
        return c;
    }
    """

/** Alpha of the glow at the outline; it fades to nothing at the extent. */
private const val HALO_PEAK_ALPHA = 0.5f
private const val WHITE_LIGHT_ALPHA = 0.8f

/** The colored light behind the element as one shader pass, without allocating per frame. */
internal class HaloGlow {
    private val shader =
        RuntimeShader(HALO_SHADER).apply {
            setColorUniform("lightA1", JellyCastColors.NeonBlue.toArgb())
            setColorUniform("lightA2", JellyCastColors.NeonPink.toArgb())
            setColorUniform("lightB1", JellyCastColors.NeonPurple.toArgb())
            setColorUniform(
                "lightB2",
                JellyCastColors.NeonWhite.copy(alpha = WHITE_LIGHT_ALPHA).toArgb(),
            )
        }
    val brush =
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shader
        }

    fun prepare(size: Size, outline: Outline, extentPx: Float) {
        shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
        shader.setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
        val radius =
            when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                else -> 0f
            }
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("extent", extentPx)
    }

    /** [turnA] and [turnB] are fractions of a full turn, clockwise. */
    fun update(turnA: Float, turnB: Float, fade: Float) {
        shader.setFloatUniform("turnA", turnA)
        shader.setFloatUniform("turnB", turnB)
        shader.setFloatUniform("strength", HALO_PEAK_ALPHA * fade)
    }
}
