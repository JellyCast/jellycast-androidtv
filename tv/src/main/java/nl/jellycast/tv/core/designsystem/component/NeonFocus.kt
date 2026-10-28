package nl.jellycast.tv.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** During key repeat focus never rests this long, so the border stays a static gradient. */
private const val COMET_DELAY_MS = 150L
private const val COMET_FADE_IN_NS = 200_000_000f
private const val COMET_LAP_NS = 5_500_000_000f
private const val SCALE_ANIM_MS = 120

private const val SPARK_VISIBLE_MIN_MS = 2_500
private const val SPARK_VISIBLE_SPREAD_MS = 1_500
private const val SPARK_HIDDEN_MIN_MS = 1_000L
private const val SPARK_HIDDEN_SPREAD_MS = 1_500
private const val SPARK_PHASE_FADE_NS = 350_000_000f
private const val NS_PER_MS = 1_000_000L

private const val HALO_FADE_IN_NS = 400_000_000f

/** One full turn of each halo light; different and opposite, so the lights keep crossing. */
private const val HALO_TURN_A_NS = 7_000_000_000f
private const val HALO_TURN_B_NS = -11_000_000_000f
private const val FULL_TURN_DEGREES = 360f

/** Alpha of the static neon gradient before the comet fades in over it. */
private const val STATIC_BORDER_ALPHA = 0.85f

/**
 * Draws the neon focus border: a static border, a comet trail with a weld spark that runs around
 * it once focus rests, and with [halo] a moving colored light behind the element instead of the
 * comet. Everything
 * happens in the draw phase of one Modifier.Node, so a focus change or comet frame never
 * recomposes or relayouts.
 *
 * [active] `null` follows the focus of the element after this modifier (put it before the
 * focusable); `true`/`false` drives it from outside. The border falls over the element's edge, so
 * put a clip after this modifier.
 */
fun Modifier.neonFocus(
    shape: Shape,
    focusScale: Float = 1f,
    active: Boolean? = null,
    halo: Boolean = false,
): Modifier = this then NeonFocusElement(shape, focusScale, active, halo)

private data class NeonFocusElement(
    val shape: Shape,
    val focusScale: Float,
    val active: Boolean?,
    val halo: Boolean,
) : ModifierNodeElement<NeonFocusNode>() {
    override fun create() = NeonFocusNode(shape, focusScale, active, halo)

    override fun update(node: NeonFocusNode) {
        node.update(shape, focusScale, active, halo)
    }
}

private class NeonFocusNode(
    private var shape: Shape,
    private var focusScale: Float,
    private var active: Boolean?,
    private var halo: Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    FocusEventModifierNode {
    private val scale = Animatable(1f)
    private var focused = false
    private var on = false
    private var cometJob: Job? = null
    private var cometStartNs = 0L
    private var frameNs = 0L
    private var haloStartNs = 0L

    private val ring = NeonRing()
    private val flicker = Flicker()

    /** No frames run outside the visible phase of the weld spark. */
    private var visibleFromNs = 0L
    private var visibleUntilNs = 0L
    private var hidden = false

    override val shouldAutoInvalidate = false

    fun update(shape: Shape, focusScale: Float, active: Boolean?, halo: Boolean) {
        if (this.shape != shape) ring.reset()
        this.shape = shape
        this.focusScale = focusScale
        this.active = active
        this.halo = halo
        setOn(active ?: focused)
        invalidateDraw()
    }

    override fun onAttach() {
        coroutineScope.launch { scale.snapTo(1f) }
        setOn(active ?: focused)
    }

    override fun onDetach() {
        // A node reused in a lazy row must start at rest.
        on = false
        cometJob = null
        cometStartNs = 0L
        haloStartNs = 0L
    }

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.hasFocus
        if (active == null) setOn(focused)
    }

    private fun setOn(value: Boolean) {
        if (value == on || !isAttached) return
        on = value
        coroutineScope.launch {
            scale.animateTo(if (on) focusScale else 1f, tween(SCALE_ANIM_MS))
        }
        cometJob?.cancel()
        cometStartNs = 0L
        haloStartNs = 0L
        hidden = false
        if (on) cometJob = if (halo) launchHalo() else launchComet()
        invalidateDraw()
    }

    /** A frame every vsync while focus rests; none during key repeat. */
    private fun launchHalo(): Job = coroutineScope.launch {
        delay(COMET_DELAY_MS)
        while (isActive) {
            withFrameNanos { now ->
                if (haloStartNs == 0L) haloStartNs = now
                frameNs = now
                invalidateDraw()
            }
        }
    }

    /** Alternates a visible phase with a frame every vsync and a hidden phase with no frames. */
    private fun launchComet(): Job = coroutineScope.launch {
        delay(COMET_DELAY_MS)
        while (isActive) {
            val start = withFrameNanos { it }
            if (cometStartNs == 0L) cometStartNs = start
            visibleFromNs = start
            val visibleMs = SPARK_VISIBLE_MIN_MS + Random.nextInt(SPARK_VISIBLE_SPREAD_MS)
            visibleUntilNs = start + visibleMs * NS_PER_MS
            hidden = false
            do {
                withFrameNanos { now ->
                    frameNs = now
                    invalidateDraw()
                }
            } while (frameNs < visibleUntilNs)
            hidden = true
            invalidateDraw()
            delay(SPARK_HIDDEN_MIN_MS + Random.nextInt(SPARK_HIDDEN_SPREAD_MS))
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scale.value
        if (!on && s == 1f) {
            drawContent()
            return
        }
        scale(s) {
            if (on && haloStartNs != 0L) drawHalo()
            this@draw.drawContent()
            if (on) drawNeon()
        }
    }

    private fun DrawScope.drawHalo() {
        ring.prepare(this, shape)
        val elapsed = frameNs - haloStartNs
        ring.drawHalo(
            this,
            degreesA = (elapsed / HALO_TURN_A_NS) % 1f * FULL_TURN_DEGREES,
            degreesB = (elapsed / HALO_TURN_B_NS) % 1f * FULL_TURN_DEGREES,
            fade = haloFade(),
        )
    }

    private fun haloFade(): Float =
        if (haloStartNs == 0L) 0f else ((frameNs - haloStartNs) / HALO_FADE_IN_NS).coerceIn(0f, 1f)

    private fun DrawScope.drawNeon() {
        ring.prepare(this, shape)
        if (halo) {
            val fade = haloFade()
            ring.drawBase(this, STATIC_BORDER_ALPHA * (1f - fade))
            if (fade > 0f) ring.drawHaloLine(this, fade)
            return
        }
        val started = cometStartNs != 0L
        val cometFade =
            if (started) {
                ((frameNs - cometStartNs) / COMET_FADE_IN_NS).coerceIn(0f, 1f)
            } else {
                0f
            }
        val staticAlpha = STATIC_BORDER_ALPHA * (1f - cometFade)
        ring.drawBase(this, staticAlpha)
        if (!started || hidden) return
        drawComet(cometFade)
    }

    private fun DrawScope.drawComet(cometFade: Float) {
        // The comet keeps moving while the phase fades, so it reappears somewhere else.
        val phase =
            minOf(
                (frameNs - visibleFromNs) / SPARK_PHASE_FADE_NS,
                (visibleUntilNs - frameNs) / SPARK_PHASE_FADE_NS,
            ).coerceIn(0f, 1f)
        val visibility = cometFade * phase
        if (visibility <= 0f) return
        val progress = ((frameNs - cometStartNs) / COMET_LAP_NS) % 1f
        val head = ring.drawComet(this, progress, visibility)
        drawWeldPoint(
            head.position,
            head.angle,
            size = 1f,
            intensity = visibility * flicker.next(frameNs),
        )
    }
}
