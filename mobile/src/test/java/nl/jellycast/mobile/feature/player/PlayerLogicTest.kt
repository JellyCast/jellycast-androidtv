package nl.jellycast.mobile.feature.player

import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.SegmentType
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.mobile.core.settings.QualityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSeekTrackerTest {
    @Test
    fun `a single tap only toggles the controls`() {
        val outcome = TapSeekTracker().tap(TapSide.Forward, nowMs = 0, positionMs = 0)

        assertEquals(TapOutcome(toggleControls = true, run = null), outcome)
    }

    @Test
    fun `a double tap toggles back and starts a ten second run`() {
        val tracker = TapSeekTracker()
        tracker.tap(TapSide.Forward, 0, 60_000)

        val outcome = tracker.tap(TapSide.Forward, 200, 60_000)

        assertTrue(outcome.toggleControls)
        assertEquals(SeekRun(TapSide.Forward, 1, 60_000), outcome.run)
    }

    @Test
    fun `a slow second tap is another single tap`() {
        val tracker = TapSeekTracker()
        tracker.tap(TapSide.Forward, 0, 0)

        assertNull(tracker.tap(TapSide.Forward, 400, 0).run)
    }

    @Test
    fun `further taps add ten seconds each and seek once`() {
        val tracker = TapSeekTracker()
        tracker.tap(TapSide.Back, 0, 60_000)
        tracker.tap(TapSide.Back, 100, 60_000)
        val third = tracker.tap(TapSide.Back, 900, 60_000)

        assertFalse(third.toggleControls)
        val run = tracker.settle()
        assertEquals(40_000L, run?.targetMs(durationMs = 100_000))
        assertNull(tracker.settle())
    }

    @Test
    fun `a run is clamped to the file`() {
        assertEquals(0L, SeekRun(TapSide.Back, 3, 10_000).targetMs(100_000))
        assertEquals(100_000L, SeekRun(TapSide.Forward, 3, 95_000).targetMs(100_000))
    }
}

class BufferWatchTest {
    @Test
    fun `a buffer that keeps draining shows the hint once`() {
        val watch = BufferWatch()
        var fired = 0
        for (second in 0..30) {
            val buffered = 40_000L - second * 500
            if (watch.sample(second * 1_000L, buffered, remainingMs = 3_600_000)) fired++
        }
        for (second in 31..120) {
            if (watch.sample(second * 1_000L, 10_000, remainingMs = 3_600_000)) fired++
        }

        assertEquals(1, fired)
    }

    @Test
    fun `a healthy buffer never shows the hint`() {
        val watch = BufferWatch()
        val fired = (0..60).any { watch.sample(it * 1_000L, 120_000L - it * 500, 3_600_000) }

        assertFalse(fired)
    }

    @Test
    fun `media3 draining its 50 s buffer between loads shows no hint`() {
        val watch = BufferWatch()
        // Media3 tops the buffer up to about 50 s, then lets it run down before loading again.
        val fired = (0..300).any { second ->
            val buffered = 50_000L - (second % 20) * 1_000L
            watch.sample(second * 1_000L, buffered, remainingMs = 3_600_000)
        }

        assertFalse(fired)
    }

    @Test
    fun `near the end of the file the window resets`() {
        val watch = BufferWatch()
        val fired = (0..30).any {
            watch.sample(it * 1_000L, 30_000L - it * 500, remainingMs = 35_000)
        }

        assertFalse(fired)
    }
}

class SegmentPillTest {
    private val next = PlayableItem.EpisodeItem(testEpisode("e2"))
    private val segments =
        listOf(
            MediaSegment(SegmentType.Intro, 10_000, 70_000),
            MediaSegment(SegmentType.Outro, 2_800_000, 3_000_000),
        )

    @Test
    fun `the intro shows a skip pill to its end`() {
        val pill = segmentPill(20_000, 3_000_000, segments, next, fromList = false, slowHint = null)

        assertEquals(SegmentPill.Skip(SegmentType.Intro, 70_000), pill)
    }

    @Test
    fun `the credits show the next pill`() {
        val pill =
            segmentPill(2_850_000, 3_000_000, segments, next, fromList = true, slowHint = null)

        assertEquals(SegmentPill.Next(next, fromList = true), pill)
    }

    @Test
    fun `without an outro the last 30 seconds count as credits`() {
        val pill = segmentPill(2_975_000, 3_000_000, emptyList(), next, false, null)

        assertTrue(pill is SegmentPill.Next)
    }

    @Test
    fun `the slow connection hint wins`() {
        val hint = SegmentPill.SlowConnection(QualityLevel.P1080)

        assertEquals(hint, segmentPill(20_000, 3_000_000, segments, next, false, hint))
    }

    @Test
    fun `nothing in the middle of the file`() {
        assertNull(segmentPill(1_000_000, 3_000_000, segments, next, false, null))
    }
}

class PlayerLayoutTest {
    @Test
    fun `a pinch past the thresholds changes the mini player size`() {
        assertTrue(pinchResult(large = false, magnification = 1.2f))
        assertFalse(pinchResult(large = true, magnification = 0.8f))
        assertTrue(pinchResult(large = true, magnification = 1.0f))
    }

    @Test
    fun `the swipe shrinks the video around the top centre and moves it down`() {
        val transform = SwipeTransform(offsetPx = 500f, heightPx = 1000f)
        val video = pinnedVideoRect(widthPx = 1000f, topPx = 0f)

        val moved = transform.apply(video, widthPx = 1000f)

        assertEquals(0.75f, transform.scale)
        assertEquals(125f, moved.left)
        assertEquals(500f, moved.top)
        assertEquals(750f, moved.width)
    }
}
