package nl.jellycast.tv.core.data

import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.StreamChoice
import nl.jellycast.tv.core.model.parsePlayLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayLinkTest {
    private val itemId = "c21c5974cfa11b6d4bef5e85b801f061"

    @Test
    fun `a play link gives the item and the start position`() {
        assertEquals(
            PlayRequest(itemId, 600_000L),
            parsePlayLink("jellycast://play?item=$itemId&start=600000"),
        )
    }

    @Test
    fun `a play link carries the audio and subtitle stream`() {
        assertEquals(
            PlayRequest(itemId, 0L, StreamChoice(1, -1)),
            parsePlayLink("jellycast://play?item=$itemId&start=0&audio=1&subtitle=-1"),
        )
    }

    @Test
    fun `without a start position it starts at the beginning`() {
        assertEquals(PlayRequest(itemId, 0L), parsePlayLink("jellycast://play?item=$itemId"))
    }

    @Test
    fun `other links and malformed ids are ignored`() {
        assertNull(parsePlayLink("https://play?item=$itemId"))
        assertNull(parsePlayLink("jellycast://open?item=$itemId"))
        assertNull(parsePlayLink("jellycast://play?item=../../etc"))
        assertNull(parsePlayLink("jellycast://play"))
        assertNull(parsePlayLink("not a link"))
    }
}
