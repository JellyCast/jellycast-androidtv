package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StallDetectorTest {
    private fun buffering(positionMs: Long, aheadMs: Long, playWhenReady: Boolean = true) =
        PlayerSnapshot(
            playWhenReady = playWhenReady,
            buffering = true,
            playing = false,
            positionMs = positionMs,
            bufferedPositionMs = positionMs + aheadMs,
        )

    private fun playing(positionMs: Long) = PlayerSnapshot(
        playWhenReady = true,
        buffering = false,
        playing = true,
        positionMs = positionMs,
        bufferedPositionMs = positionMs + 30_000,
    )

    /** Checks [snapshot] every second until an action; returns its time and the action. */
    private fun StallDetector.runUntilAction(
        fromMs: Long,
        seconds: Int,
        snapshot: (Long) -> PlayerSnapshot,
    ): Pair<Long, StallAction> {
        for (i in 0..seconds) {
            val now = fromMs + i * 1_000L
            val action = check(now, snapshot(now))
            if (action != StallAction.None) return now to action
        }
        return fromMs + seconds * 1_000L to StallAction.None
    }

    @Test
    fun `buffering with plenty ahead reseeks after 4 s`() {
        val (at, action) = StallDetector().runUntilAction(0, 10) { buffering(60_000, 20_000) }
        assertEquals(StallAction.Reseek(StallKind.BufferingWithData), action)
        assertEquals(4_000L, at)
    }

    @Test
    fun `normal buffering with little ahead is no stall`() {
        // Beyond 10 s it counts as too slow (see below), not as a stall.
        val (_, action) = StallDetector().runUntilAction(0, 9) { buffering(60_000, 3_000) }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `buffering while paused is no stall`() {
        val (_, action) =
            StallDetector().runUntilAction(0, 60) {
                buffering(60_000, 20_000, playWhenReady = false)
            }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `a frozen clock during playback reseeks after 3 s`() {
        val (at, action) = StallDetector().runUntilAction(0, 10) { playing(60_000) }
        assertEquals(StallAction.Reseek(StallKind.ClockStopped), action)
        // Frozen counts from the second reading with the same position (1 s), then 3 s.
        assertEquals(4_000L, at)
    }

    @Test
    fun `a running clock is no stall`() {
        val (_, action) = StallDetector().runUntilAction(0, 120) { now -> playing(now) }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `a stall that returns within a minute escalates`() {
        val detector = StallDetector()
        val (first, _) = detector.runUntilAction(0, 10) { playing(60_000) }
        // Fine for a moment, then stuck again.
        detector.runUntilAction(first + 1_000, 5) { now -> playing(now) }
        val (_, again) = detector.runUntilAction(first + 10_000, 10) { playing(90_000) }
        assertEquals(StallAction.Escalate(StallKind.ClockStopped), again)
    }

    @Test
    fun `after a minute a new stall counts as the first again`() {
        val detector = StallDetector()
        detector.runUntilAction(0, 10) { playing(60_000) }
        detector.runUntilAction(5_000, 70) { now -> playing(now) }
        val (_, later) = detector.runUntilAction(80_000, 10) { playing(200_000) }
        assertEquals(StallAction.Reseek(StallKind.ClockStopped), later)
    }

    @Test
    fun `a stalled player maps to the matching fallback step`() {
        assertEquals(PlaybackFailure.Video, StallKind.BufferingWithData.toFailure())
        assertEquals(PlaybackFailure.Audio, StallKind.ClockStopped.toFailure())
    }

    private fun playingDetector() = StallDetector().apply { check(0, playing(0)) }

    @Test
    fun `10 s of starved buffering is too slow`() {
        val (at, action) = playingDetector().runUntilAction(1_000, 20) { buffering(60_000, 1_000) }
        assertEquals(StallAction.Escalate(StallKind.TooSlow), action)
        assertEquals(11_000L, at)
    }

    @Test
    fun `loading before playback starts is not too slow`() {
        val (_, action) = StallDetector().runUntilAction(0, 20) { buffering(0, 1_000) }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `loading after a seek is not too slow`() {
        val detector = playingDetector()
        detector.onSeek()
        val (_, action) = detector.runUntilAction(1_000, 20) { buffering(60_000, 1_000) }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `loading after a seek does not add to earlier buffering`() {
        val detector = playingDetector()
        detector.runUntilAction(1_000, 8) { buffering(60_000, 1_000) }
        detector.runUntilAction(10_000, 5) { now -> playing(now) }
        detector.onSeek()
        val (_, action) = detector.runUntilAction(16_000, 20) { buffering(90_000, 1_000) }
        assertEquals(StallAction.None, action)
    }

    @Test
    fun `starting that never gets going is too slow after 30 s`() {
        val (at, action) = StallDetector().runUntilAction(0, 40) { buffering(0, 1_000) }
        assertEquals(StallAction.Escalate(StallKind.TooSlow), action)
        assertEquals(30_000L, at)
    }

    @Test
    fun `repeated short buffering adds up to too slow`() {
        val detector = playingDetector()
        var now = 1_000L
        var result: StallAction = StallAction.None
        // 4 s buffering, 6 s playing, repeated: after 5 rounds 20 s buffering within 2 minutes.
        repeat(6) {
            for (i in 0 until 4) {
                result =
                    detector.check(now, buffering(60_000, 1_000)).takeIf { it != StallAction.None }
                        ?: result
                now += 1_000
            }
            for (i in 0 until 6) {
                result =
                    detector.check(now, playing(now)).takeIf { it != StallAction.None } ?: result
                now += 1_000
            }
        }
        assertEquals(StallAction.Escalate(StallKind.TooSlow), result)
    }

    @Test
    fun `occasional short buffering is not too slow`() {
        val detector = playingDetector()
        var now = 1_000L
        repeat(10) {
            for (i in 0 until 3) {
                assertEquals(StallAction.None, detector.check(now, buffering(60_000, 1_000)))
                now += 1_000
            }
            for (i in 0 until 60) {
                assertEquals(StallAction.None, detector.check(now, playing(now)))
                now += 1_000
            }
        }
    }
}
