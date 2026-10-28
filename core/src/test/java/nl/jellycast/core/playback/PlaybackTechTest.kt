package nl.jellycast.core.playback

import nl.jellycast.core.model.AudioTrack
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTechTest {
    private val dts = AudioTrack(1, "eng", "dts", 6, "DTS-HD MA", isDefault = true)
    private val ac3 = AudioTrack(2, "eng", "ac3", 6, "AC3", isDefault = false)

    private fun plan(audioIndex: Int?, method: PlayMethod = PlayMethod.DirectPlay) = PlaybackPlan(
        itemId = "m",
        title = "m",
        mediaSourceId = "m",
        playSessionId = null,
        streamUrl = "",
        playMethod = method,
        transcodeReasons = emptyList(),
        container = "mkv",
        video = null,
        audioTracks = listOf(dts, ac3),
        subtitleTracks = emptyList(),
        audioStreamIndex = audioIndex,
        subtitleStreamIndex = null,
        startPositionMs = 0,
    )

    @Test
    fun `a track other than the default is shown as a replacement`() {
        val tech = plan(audioIndex = 2).toTech()

        assertEquals(ac3, tech.audio)
        assertEquals(dts, tech.replacedAudio)
        assertEquals(PlayMethod.DirectPlay, tech.playMethod)
    }

    @Test
    fun `the default track playing means no replacement`() {
        val tech = plan(audioIndex = 1, method = PlayMethod.DirectStream).toTech()

        assertEquals(dts, tech.audio)
        assertNull(tech.replacedAudio)
        assertEquals(PlayMethod.DirectStream, tech.playMethod)
    }

    @Test
    fun `a track in another language is no replacement`() {
        val ita = AudioTrack(1, "ita", "ac3", 6, "Italian", isDefault = true)
        val eng = AudioTrack(2, "eng", "eac3", 6, "English", isDefault = false)
        val tech = plan(audioIndex = 2).copy(audioTracks = listOf(ita, eng)).toTech()

        assertEquals(eng, tech.audio)
        assertNull(tech.replacedAudio)
    }
}
