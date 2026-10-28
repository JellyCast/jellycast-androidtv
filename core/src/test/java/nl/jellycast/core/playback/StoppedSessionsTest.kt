package nl.jellycast.core.playback

import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoppedSessionsTest {
    private fun plan() = PlaybackPlan(
        itemId = "e1",
        title = "e1",
        mediaSourceId = "e1",
        playSessionId = null,
        streamUrl = "",
        playMethod = PlayMethod.DirectPlay,
        transcodeReasons = emptyList(),
        container = "mkv",
        video = null,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        startPositionMs = 0,
    )

    @Test
    fun `after the stop there is no more progress for that plan`() {
        val sessions = StoppedSessions()
        val plan = plan()
        assertFalse(sessions.isStopped(plan))

        sessions.stop(plan)

        assertTrue(sessions.isStopped(plan))
    }

    @Test
    fun `a restart with a new plan for the same item reports progress again`() {
        val sessions = StoppedSessions()
        sessions.stop(plan())

        assertFalse(sessions.isStopped(plan()))
    }
}
