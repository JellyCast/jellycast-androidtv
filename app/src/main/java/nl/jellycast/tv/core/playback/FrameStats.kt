package nl.jellycast.tv.core.playback

import java.util.concurrent.atomic.AtomicLong

/**
 * Frames released to the display versus frames Android reports as shown. The difference is frames
 * the compositor dropped, which Media3's dropped-frame counter never sees. Not available with
 * tunneling: Android then reports nothing per frame.
 */
class FrameStats {
    private val released = AtomicLong()
    private val rendered = AtomicLong()

    @Volatile var tunneled = false
        private set

    fun onReleased() {
        released.incrementAndGet()
    }

    fun onRendered() {
        rendered.incrementAndGet()
    }

    fun markTunneled() {
        tunneled = true
    }

    val notShown: Long
        get() = (released.get() - rendered.get() - IN_FLIGHT).coerceAtLeast(0)

    val shown: Long
        get() = rendered.get()

    fun summary(): String? = if (tunneled || released.get() == 0L) {
        null
    } else {
        "$notShown of ${notShown + shown} frames not shown"
    }

    private companion object {
        /** Frames that may still be queued for the display when playback stops. */
        const val IN_FLIGHT = 3L
    }
}
