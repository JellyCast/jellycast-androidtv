package nl.jellycast.core.playback

/**
 * Gives each video frame its own vsync. Two frames too close together (bundled timestamps, or MKV
 * rounding to 41/42 ms against a 41.7 ms vsync) land on the same vsync and the display shows only
 * one, without Media3 counting a dropped frame. Such a frame moves one vsync later, at most
 * [MAX_SHIFTS_IN_A_ROW] times in a row, so audio and video never drift apart.
 */
class FramePacer {
    private var previousNs: Long? = null
    private var shiftsInARow = 0

    fun pace(releaseNs: Long, vsyncNs: Long): Long {
        val previous = previousNs
        val collides =
            previous != null &&
                vsyncNs > 0 &&
                shiftsInARow < MAX_SHIFTS_IN_A_ROW &&
                // Half a vsync rather than equality, because the vsync grid drifts slightly.
                kotlin.math.abs(releaseNs - previous) <= vsyncNs / 2
        val result = if (collides) requireNotNull(previous) + vsyncNs else releaseNs
        shiftsInARow = if (collides) shiftsInARow + 1 else 0
        previousNs = result
        return result
    }

    companion object {
        const val MAX_SHIFTS_IN_A_ROW = 6
    }
}
