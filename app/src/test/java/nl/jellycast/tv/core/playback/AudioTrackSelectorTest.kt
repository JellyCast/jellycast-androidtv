package nl.jellycast.tv.core.playback

import nl.jellycast.tv.core.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTrackSelectorTest {
    private fun track(
        index: Int,
        codec: String,
        language: String = "eng",
        channels: Int = 6,
        isDefault: Boolean = false,
    ) = AudioTrack(
        index = index,
        language = language,
        codec = codec,
        channels = channels,
        title = null,
        isDefault = isDefault,
    )

    private val defaults = PlaybackSettings()

    private val serverConverts = PlaybackSettings(decodeLosslessAudioLocally = false)

    /** Soundbar on plain ARC: AC3/EAC3 passthrough, but only stereo PCM. */
    private val arcSoundbar = ChromecastCapabilities.copy(maxPcmChannels = 2)

    @Test
    fun `no tracks means no choice`() {
        assertNull(selectAudioTrack(emptyList(), ChromecastCapabilities, defaults))
    }

    @Test
    fun `a default track that can pass through stays chosen`() {
        val tracks = listOf(track(1, "eac3", isDefault = true), track(2, "ac3"))
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `TrueHD becomes EAC3 in the same language`() {
        val tracks =
            listOf(
                track(1, "truehd", channels = 8, isDefault = true),
                track(2, "eac3", channels = 8),
                track(3, "eac3", channels = 6),
                track(4, "ac3"),
            )
        assertEquals(2, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `DTS-HD without EAC3 falls back to AC3`() {
        val tracks = listOf(track(1, "dts", isDefault = true), track(2, "ac3"))
        assertEquals(2, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `the alternative must have the same language`() {
        val tracks =
            listOf(
                track(1, "truehd", language = "eng", isDefault = true),
                track(2, "ac3", language = "ita"),
            )
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `with local decoding TrueHD stays chosen`() {
        val tracks = listOf(track(1, "truehd", isDefault = true), track(2, "ac3"))
        val settings = PlaybackSettings(decodeLosslessAudioLocally = true)
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, settings)?.index)
    }

    @Test
    fun `a device that passes TrueHD through keeps TrueHD`() {
        val tracks = listOf(track(1, "truehd", isDefault = true), track(2, "ac3"))
        val receiver = ChromecastCapabilities.copy(
            passthroughAudio = setOf("ac3", "eac3", "truehd"),
        )
        assertEquals(1, selectAudioTrack(tracks, receiver, defaults)?.index)
    }

    @Test
    fun `the preferred language beats the default track`() {
        val tracks =
            listOf(
                track(1, "eac3", language = "eng", isDefault = true),
                track(2, "eac3", language = "nld"),
            )
        assertEquals(
            2,
            selectAudioTrack(
                tracks,
                ChromecastCapabilities,
                defaults,
                preferredLanguages = listOf("nld"),
            )?.index,
        )
    }

    @Test
    fun `the original language beats a dubbed default track`() {
        val tracks =
            listOf(
                track(1, "ac3", language = "ita", isDefault = true),
                track(2, "eac3", language = "eng"),
            )
        assertEquals(
            2,
            selectAudioTrack(
                tracks,
                ChromecastCapabilities,
                defaults,
                preferredLanguages = listOf("en") + FallbackAudioLanguages,
            )?.index,
        )
    }

    @Test
    fun `a French original plays in French even next to an English dub`() {
        val tracks =
            listOf(
                track(1, "eac3", language = "eng", isDefault = true),
                track(2, "ac3", language = "fre"),
            )
        assertEquals(
            2,
            selectAudioTrack(
                tracks,
                ChromecastCapabilities,
                defaults,
                preferredLanguages = listOf("fr") + FallbackAudioLanguages,
            )?.index,
        )
    }

    @Test
    fun `without a known original language English beats a dubbed default`() {
        val tracks =
            listOf(
                track(1, "ac3", language = "spa", isDefault = true),
                track(2, "ac3", language = "nld"),
                track(3, "eac3", language = "eng"),
            )
        assertEquals(3, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `Dutch comes after English`() {
        val tracks =
            listOf(track(1, "ac3", language = "ita", isDefault = true), track(2, "ac3", "dut"))
        assertEquals(2, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `without a preferred language the default track stays`() {
        val tracks =
            listOf(track(1, "ac3", language = "fra", isDefault = true), track(2, "ac3", "deu"))
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `a commentary track is never the language choice`() {
        val tracks =
            listOf(
                track(1, "ac3", language = "ita", isDefault = true),
                AudioTrack(2, "eng", "ac3", 2, "Commentary", isDefault = false),
                track(3, "eac3", language = "eng"),
            )
        assertEquals(3, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `language codes from tags and Wikidata compare as ISO 639-2 T`() {
        assertEquals("fra", normalizeLanguage("fre"))
        assertEquals("fra", normalizeLanguage("fr"))
        assertEquals("nld", normalizeLanguage("NL"))
        assertEquals("eng", normalizeLanguage("eng"))
        assertNull(normalizeLanguage("und"))
        assertNull(normalizeLanguage(null))
    }

    @Test
    fun `without a default track the first track is chosen`() {
        val tracks = listOf(track(3, "aac"), track(4, "ac3"))
        assertEquals(3, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `commentary is never the replacement`() {
        val tracks =
            listOf(
                track(1, "truehd", channels = 8, isDefault = true),
                AudioTrack(2, "eng", "ac3", 2, "Commentary by the director", isDefault = false),
                AudioTrack(3, "eng", "ac3", 6, "Compatibility Track", isDefault = false),
            )
        assertEquals(3, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `without passthrough decoding EAC3 locally beats a server TrueHD transcode`() {
        val tracks = listOf(track(1, "truehd", isDefault = true), track(2, "eac3"))
        val stereoTv = ChromecastCapabilities.copy(passthroughAudio = emptySet())
        assertEquals(2, selectAudioTrack(tracks, stereoTv, serverConverts)?.index)
    }

    @Test
    fun `an alternative the device cannot play does not count`() {
        val tracks = listOf(track(1, "truehd", isDefault = true), track(2, "eac3"))
        assertEquals(1, selectAudioTrack(tracks, BasicCapabilities, serverConverts)?.index)
    }

    @Test
    fun `DTS-HD without EAC3 or AC3 uses PCM with as many channels`() {
        val tracks = listOf(track(1, "dts", isDefault = true), track(2, "pcm_s24le", channels = 6))
        assertEquals(2, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `stereo PCM does not replace 5_1`() {
        val tracks =
            listOf(
                track(1, "dts", channels = 6, isDefault = true),
                track(2, "dts", channels = 6),
                track(3, "pcm_s24le", channels = 2),
                track(4, "pcm_s24le", channels = 2),
            )
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `EAC3 beats PCM to keep Atmos via passthrough`() {
        val tracks =
            listOf(
                track(1, "truehd", channels = 8, isDefault = true),
                track(2, "pcm_s24le", channels = 8),
                track(3, "eac3", channels = 6),
            )
        assertEquals(3, selectAudioTrack(tracks, ChromecastCapabilities, serverConverts)?.index)
    }

    @Test
    fun `by default TrueHD next to EAC3 Atmos picks EAC3`() {
        val tracks =
            listOf(
                track(1, "truehd", channels = 8, isDefault = true),
                track(2, "eac3", channels = 8),
            )
        assertEquals(2, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `by default DTS-HD next to only AC3 stays DTS-HD, decoded lossless locally`() {
        val tracks = listOf(track(1, "dts", channels = 8, isDefault = true), track(2, "ac3"))
        assertEquals(1, selectAudioTrack(tracks, ChromecastCapabilities, defaults)?.index)
    }

    @Test
    fun `a soundbar on ARC prefers AC3 passthrough over local stereo PCM`() {
        val tracks = listOf(track(1, "dts", channels = 8, isDefault = true), track(2, "ac3"))
        assertEquals(2, selectAudioTrack(tracks, arcSoundbar, defaults)?.index)
    }

    @Test
    fun `DTS-HD stays chosen when the output takes DTS, as Media3 sends the DTS core`() {
        // An AVR with plain DTS only: the 6-channel DTS probe puts "dts" in the set.
        val dtsAvr = ChromecastCapabilities.copy(
            passthroughAudio = setOf("ac3", "eac3", "dts", "dca"),
        )
        val tracks = listOf(track(1, "dts", channels = 8, isDefault = true), track(2, "ac3"))
        assertEquals(1, selectAudioTrack(tracks, dtsAvr, defaults)?.index)
    }
}
