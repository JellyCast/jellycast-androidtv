package nl.jellycast.tv.core.model

import java.util.Locale
import nl.jellycast.tv.testing.FakeGenreNames
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelvesTest {
    private data class Item(val name: String, val genres: List<String>)

    @Test
    fun `fixed order, genres alphabetical by Dutch name`() {
        val all =
            listOf(
                Item("a", listOf("Crime", "Action")),
                Item("b", listOf("Crime", "Action")),
                Item("c", listOf("Crime", "Action", "Drama")),
                Item("d", listOf("Drama")),
            )

        val shelves =
            buildShelves(
                continueWatching = listOf(all[0]),
                new = listOf(all[3]),
                all = all,
                genreNames = FakeGenreNames(),
                genresOf = { it.genres },
            )

        assertEquals(
            listOf(
                ShelfKind.ContinueWatching,
                ShelfKind.New,
                ShelfKind.Genre("Actie"),
                ShelfKind.Genre("Misdaad"),
            ),
            shelves.map { it.kind },
        )
        // Within a genre the A–Z order of [all] is kept.
        assertEquals(listOf("a", "b", "c"), shelves.last().items.map { it.name })
    }

    @Test
    fun `empty shelves and genres with fewer than 3 titles are dropped`() {
        val all = listOf(Item("a", listOf("Drama")), Item("b", listOf("Drama")))

        val shelves = buildShelves(emptyList(), emptyList(), all, FakeGenreNames()) { it.genres }

        assertEquals(emptyList<ShelfKind>(), shelves.map { it.kind })
    }

    @Test
    fun `new - unwatched, last 30 days, most recently added first`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        data class Added(val name: String, val addedAt: Long?, val played: Boolean = false)
        val items =
            listOf(
                Added("old", now - 31 * day),
                Added("watched", now - day, played = true),
                Added("no date", null),
                Added("boundary", now - 30 * day),
                Added("yesterday", now - day),
            )

        val new = newShelfItems(items, now, addedAt = { it.addedAt }, isPlayed = { it.played })

        assertEquals(listOf("yesterday", "boundary"), new.map { it.name })
    }

    @Test
    fun `new - at most 20 items`() {
        val items = List(25) { it.toLong() }

        val new = newShelfItems(items, nowMs = 25, addedAt = { it }, isPlayed = { false })

        assertEquals(NEW_SHELF_SIZE, new.size)
        assertEquals(24L, new.first())
    }

    @Test
    fun `genres alphabetical in the language itself, accents included`() {
        val french =
            FakeGenreNames(
                mapOf("Horror" to "Épouvante", "Drama" to "Drame", "Western" to "Western"),
                Locale.FRENCH,
            )
        val all = List(3) { Item("$it", listOf("Western", "Horror", "Drama")) }

        val shelves = genreShelves(all, french) { it.genres }

        assertEquals(
            listOf("Drame", "Épouvante", "Western"),
            shelves.map { (it.kind as ShelfKind.Genre).name },
        )
    }

    @Test
    fun `progress and remaining time`() {
        assertEquals(0.25f, progressOf(resumePositionMs = 25, runtimeMs = 100))
        assertEquals(null, progressOf(resumePositionMs = 0, runtimeMs = 100))
        assertEquals(null, progressOf(resumePositionMs = 10, runtimeMs = null))
        assertEquals(75L, remainingMs(resumePositionMs = 25, runtimeMs = 100))
        assertEquals(null, remainingMs(resumePositionMs = 0, runtimeMs = 100))
    }
}
