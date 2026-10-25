package nl.jellycast.tv.core.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropChoiceTest {
    private val choice = BackdropChoice(Random(seed = 7))

    @Test
    fun `every screen gets the same backdrop until the choice moves on`() {
        val first = choice.index("movie", count = 5)
        repeat(10) { assertEquals(first, choice.index("movie", count = 5)) }
    }

    @Test
    fun `moving on shows each backdrop in turn and never the same twice in a row`() {
        val seen = mutableListOf(choice.index("movie", count = 3))
        repeat(3) {
            choice.advance(listOf("movie"))
            seen += choice.index("movie", count = 3)
        }
        seen.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals(setOf(0, 1, 2), seen.toSet())
    }

    @Test
    fun `moving on only touches the given items and signals a change`() {
        val other = choice.index("other", count = 5)
        choice.index("movie", count = 5)

        choice.advance(listOf("movie"))

        assertEquals(other, choice.index("other", count = 5))
        assertEquals(1, choice.changes.value)
    }

    @Test
    fun `items with one backdrop or never shown do not signal a change`() {
        choice.index("single", count = 1)
        choice.advance(listOf("single", "unknown"))
        assertEquals(0, choice.changes.value)
    }

    @Test
    fun `a different number of backdrops picks one that exists`() {
        repeat(5) {
            choice.index("movie", count = 5)
            choice.advance(listOf("movie"))
        }
        assertTrue(choice.index("movie", count = 2) in 0..1)
    }
}
