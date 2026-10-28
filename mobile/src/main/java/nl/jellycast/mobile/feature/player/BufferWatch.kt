package nl.jellycast.mobile.feature.player

/**
 * The slow-connection hint (iOS `BufferWatch`): sampled every second while playing; fires when,
 * over at least 27 s of a 30 s window, the buffer ahead dropped by 5 s or more and is now under
 * 15 s. At most one hint per 5 minutes, also across loads.
 */
class BufferWatch {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var lastHintAtMs: Long? = null

    /** The window starts again after a pause, a seek or near the end of the file. */
    fun reset() = samples.clear()

    /** Returns `true` when the hint should show now. */
    fun sample(nowMs: Long, bufferedAheadMs: Long, remainingMs: Long): Boolean {
        if (remainingMs - bufferedAheadMs <= NEAR_END_MS) {
            reset()
            return false
        }
        samples.addLast(nowMs to bufferedAheadMs)
        while (nowMs - samples.first().first > WINDOW_MS) samples.removeFirst()
        val (firstAt, firstBuffered) = samples.first()
        val dropped = firstBuffered - bufferedAheadMs
        val fires =
            nowMs - firstAt >= MIN_SPAN_MS &&
                dropped >= MIN_DROP_MS &&
                bufferedAheadMs < LOW_BUFFER_MS &&
                lastHintAtMs?.let { nowMs - it >= HINT_INTERVAL_MS } != false
        if (fires) {
            lastHintAtMs = nowMs
            reset()
        }
        return fires
    }

    private companion object {
        const val WINDOW_MS = 30_000L
        const val MIN_SPAN_MS = 27_000L
        const val MIN_DROP_MS = 5_000L

        // iOS loads minutes ahead, so under a minute means trouble there. Media3 keeps about 50 s
        // and lets it drain between loads, so only a buffer far below that means the network lags.
        const val LOW_BUFFER_MS = 15_000L
        const val NEAR_END_MS = 10_000L
        const val HINT_INTERVAL_MS = 5 * 60_000L
    }
}
