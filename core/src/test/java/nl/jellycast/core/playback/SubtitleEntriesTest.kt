package nl.jellycast.core.playback

import java.util.Locale
import nl.jellycast.core.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEntriesTest {
    private val dutch = Locale.forLanguageTag("nl")

    private fun track(
        language: String?,
        label: String? = null,
        codec: String = "SRT",
        isSdh: Boolean = false,
        isForced: Boolean = false,
    ) = TrackOption(
        groupIndex = 0,
        label = label,
        language = language,
        codec = codec,
        channels = null,
        isForced = isForced,
        isSdh = isSdh,
        isSelected = false,
    )

    @Test
    fun `preferred languages come first, the rest alphabetically in the app language`() {
        val tracks =
            listOf("en", "ar", "de", "nl", "fr", "es").map { track(it) } +
                track("en", label = "SDH")

        val entries = subtitleEntries(tracks, listOf("nld", "eng"), dutch)

        assertEquals(
            listOf("Nederlands", "Engels", "Engels", "Arabisch", "Duits", "Frans", "Spaans"),
            entries.map { it.language },
        )
        assertTrue(entries[2].isSdh)
    }

    @Test
    fun `an SDH title shows as the language with an SDH mark`() {
        val entry = subtitleEntries(
            listOf(track("eng", label = "SDH")),
            emptyList(),
            dutch,
        ).single()

        assertEquals("Engels", entry.language)
        assertNull(entry.title)
        assertTrue(entry.isSdh)
    }

    @Test
    fun `a title that only repeats language, flags or format is dropped`() {
        val titles =
            listOf("English", "English [Forced]", "Engels", "Full SRT", "English (SDH)")
        val entries =
            subtitleEntries(titles.map { track("eng", label = it) }, emptyList(), dutch)

        entries.forEach { assertNull(it.title) }
        assertTrue(entries.single { it.option.label == "English [Forced]" }.isForced)
    }

    @Test
    fun `a title that adds something stays`() {
        val entries =
            subtitleEntries(
                listOf(track("zho", label = "Traditional"), track("zho", label = "Simplified")),
                emptyList(),
                dutch,
            )

        assertEquals(listOf("Traditional", "Simplified"), entries.map { it.title })
        entries.forEach { assertNull(it.number) }
    }

    @Test
    fun `the same release name on every track says nothing`() {
        val name = "Movie.2024.2160p"
        val entries =
            subtitleEntries(listOf(track("eng", name), track("nld", name)), emptyList(), dutch)

        entries.forEach { assertNull(it.title) }
    }

    @Test
    fun `tracks that would look the same get a number`() {
        val entries =
            subtitleEntries(listOf(track("spa"), track("spa")), emptyList(), dutch)

        assertEquals(listOf(null, 2), entries.map { it.number })
    }

    @Test
    fun `within a language plain comes before SDH and forced comes last`() {
        val entries =
            subtitleEntries(
                listOf(
                    track("eng", isForced = true),
                    track("eng", isSdh = true),
                    track("eng"),
                ),
                emptyList(),
                dutch,
            )

        assertEquals(listOf(false, true, false), entries.map { it.isSdh })
        assertTrue(entries.last().isForced)
    }

    @Test
    fun `image subtitles are marked and tracks without language go last`() {
        val entries =
            subtitleEntries(
                listOf(track(null, label = "Commentary"), track("eng", codec = "PGS")),
                emptyList(),
                dutch,
            )

        assertTrue(entries.first().isImage)
        assertEquals("Commentary", entries.last().title)
    }

    @Test
    fun `tracks without a language sort by their name, after every language`() {
        val entries =
            subtitleEntries(
                listOf(
                    track(null, label = "Signs"),
                    track("spa"),
                    track(null, label = "Commentary"),
                ),
                emptyList(),
                dutch,
            )

        assertEquals(listOf("Spaans", null, null), entries.map { it.language })
        assertEquals(listOf(null, "Commentary", "Signs"), entries.map { it.title })
    }

    @Test
    fun `tracks that look the same keep the file's order`() {
        val tracks = listOf(3, 1, 2).map { track("eng").copy(groupIndex = it) }

        val entries = subtitleEntries(tracks, emptyList(), dutch)

        assertEquals(listOf(3, 1, 2), entries.map { it.option.groupIndex })
    }

    @Test
    fun `a server track keeps its stream index, SDH flag and image format`() {
        val server =
            SubtitleTrack(
                index = 7,
                language = "dut",
                codec = "PGSSUB",
                title = null,
                isDefault = false,
                isForced = false,
                isHearingImpaired = true,
            )

        val entry = subtitleEntries(listOf(server.toTrackOption(true)), emptyList(), dutch).single()

        assertEquals(7, entry.option.groupIndex)
        assertEquals("Nederlands", entry.language)
        assertTrue(entry.isSdh)
        assertTrue(entry.isImage)
        assertTrue(entry.option.isSelected)
    }
}
