package nl.jellycast.tv.core.data

import nl.jellycast.tv.core.data.mapper.parseRemoteCommand
import nl.jellycast.tv.core.model.RemoteCommand
import nl.jellycast.tv.core.model.StreamChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteControlMappersTest {
    private fun playstate(command: String, extra: String = "") =
        """{"MessageType":"Playstate","Data":{"Command":"$command"$extra}}"""

    private fun general(name: String, index: String) =
        """{"MessageType":"GeneralCommand","Data":{"Name":"$name","Arguments":{"Index":"$index"}}}"""

    @Test
    fun `pause, unpause, play-pause, stop and next track map one to one`() {
        assertEquals(RemoteCommand.Pause, parseRemoteCommand(playstate("Pause")))
        assertEquals(RemoteCommand.Unpause, parseRemoteCommand(playstate("Unpause")))
        assertEquals(RemoteCommand.PlayPause, parseRemoteCommand(playstate("PlayPause")))
        assertEquals(RemoteCommand.Stop, parseRemoteCommand(playstate("Stop")))
        assertEquals(RemoteCommand.NextEpisode, parseRemoteCommand(playstate("NextTrack")))
    }

    @Test
    fun `a seek converts ticks to milliseconds`() {
        assertEquals(
            RemoteCommand.Seek(1_421_000L),
            parseRemoteCommand(playstate("Seek", ""","SeekPositionTicks":14210000000""")),
        )
        assertNull(parseRemoteCommand(playstate("Seek")))
    }

    @Test
    fun `stream commands carry the Jellyfin index, with -1 as off`() {
        assertEquals(
            RemoteCommand.SetAudioStream(2),
            parseRemoteCommand(general("SetAudioStreamIndex", "2")),
        )
        assertEquals(
            RemoteCommand.SetSubtitleStream(RemoteCommand.NO_STREAM),
            parseRemoteCommand(general("SetSubtitleStreamIndex", "-1")),
        )
        assertNull(parseRemoteCommand(general("SetAudioStreamIndex", "two")))
    }

    @Test
    fun `play now starts the first item at the asked position, in the app's id format`() {
        val message =
            """{"MessageType":"Play","Data":{"PlayCommand":"PlayNow",""" +
                """"ItemIds":["c21c5974-cfa1-1b6d-4bef-5e85b801f061"],""" +
                """"StartPositionTicks":6000000000}}"""
        assertEquals(
            RemoteCommand.Play("c21c5974cfa11b6d4bef5e85b801f061", 600_000L),
            parseRemoteCommand(message),
        )
    }

    @Test
    fun `play now carries the tracks the phone played`() {
        val message =
            """{"MessageType":"Play","Data":{"PlayCommand":"PlayNow","ItemIds":["a"],""" +
                """"StartPositionTicks":0,"AudioStreamIndex":1,"SubtitleStreamIndex":3}}"""
        assertEquals(
            RemoteCommand.Play("a", 0, StreamChoice(1, 3)),
            parseRemoteCommand(message),
        )
    }

    @Test
    fun `what the player doesn't act on is ignored`() {
        assertNull(parseRemoteCommand(playstate("Rewind")))
        assertNull(parseRemoteCommand(general("ToggleMute", "0")))
        assertNull(
            parseRemoteCommand(
                """{"MessageType":"Play","Data":{"PlayCommand":"PlayNext","ItemIds":["a"]}}""",
            ),
        )
        assertNull(parseRemoteCommand("""{"MessageType":"ForceKeepAlive","Data":60}"""))
        assertNull(parseRemoteCommand("not json"))
    }
}
