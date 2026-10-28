package nl.jellycast.mobile.feature.player

enum class TapSide(val direction: Int) { Back(-1), Forward(1) }

/** Taps on one side add [SEEK_STEP_MS] each; the run seeks once, after the taps settle. */
data class SeekRun(val side: TapSide, val taps: Int, val startPositionMs: Long) {
    val offsetSeconds: Int get() = taps * (SEEK_STEP_MS / 1000).toInt()

    fun targetMs(durationMs: Long): Long {
        val target = startPositionMs + side.direction * taps * SEEK_STEP_MS
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return target.coerceIn(0, upper)
    }
}

/** What a tap does: show or hide the controls, and the seek run now in progress. */
data class TapOutcome(val toggleControls: Boolean, val run: SeekRun?)

/**
 * iOS `TapToSeekLayer` without the gesture system's double-tap delay: a single tap toggles the
 * controls at once; a second tap on the same side within [DOUBLE_TAP_WINDOW_MS] toggles them back
 * and starts a run; further taps on that side extend it until [settle] is called.
 */
class TapSeekTracker {
    private var lastTap: Pair<TapSide, Long>? = null
    private var run: SeekRun? = null

    fun tap(side: TapSide, nowMs: Long, positionMs: Long): TapOutcome {
        val active = run
        if (active != null) {
            run =
                if (active.side == side) {
                    active.copy(taps = active.taps + 1)
                } else {
                    SeekRun(side, taps = 1, startPositionMs = positionMs)
                }
            lastTap = side to nowMs
            return TapOutcome(toggleControls = false, run = run)
        }
        val previous = lastTap
        lastTap = side to nowMs
        if (previous != null && previous.first == side && nowMs - previous.second <= DOUBLE_TAP_WINDOW_MS) {
            run = SeekRun(side, taps = 1, startPositionMs = positionMs)
            return TapOutcome(toggleControls = true, run = run)
        }
        return TapOutcome(toggleControls = true, run = null)
    }

    /** The run to seek now, after [SEEK_SETTLE_MS] without taps; ends it. */
    fun settle(): SeekRun? {
        val finished = run
        run = null
        lastTap = null
        return finished
    }
}

const val SEEK_STEP_MS = 10_000L
const val DOUBLE_TAP_WINDOW_MS = 300L
const val SEEK_SETTLE_MS = 650L
