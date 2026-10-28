package nl.jellycast.mobile.core.chat

import nl.jellycast.core.model.ChatCredit
import nl.jellycast.core.model.ChatCredits
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextTest {
    private val series = testSeries("s").copy(overview = "A teacher cooks.")
    private val episodes =
        listOf(
            testEpisode("e1", "s", season = 1, number = 1),
            testEpisode("e2", "s", season = 1, number = 2),
            testEpisode("e3", "s", season = 2, number = 1),
            testEpisode("special", "s", season = 0, number = 1),
        )

    @Test
    fun `the spoiler window ends with the current episode and skips specials`() {
        assertEquals(
            listOf("e1", "e2"),
            ChatContext.episodesUpTo(episodes[1], episodes).map { it.id },
        )
    }

    @Test
    fun `a special is told after all regular episodes`() {
        assertEquals(
            listOf("e1", "e2", "e3", "special"),
            ChatContext.episodesUpTo(episodes[3], episodes).map { it.id },
        )
    }

    @Test
    fun `an episode's prompt names the episode and forbids what comes after it`() {
        val subject = ChatSubject.EpisodeSubject(episodes[1], series, episodes)

        val prompt = ChatContext.system(
            subject,
            cast = null,
            avoidsSpoilers = true,
            searchesWeb = false,
        )

        assertTrue("The user is watching Serie s (2020), S1E2 \"Episode e2\"." in prompt)
        assertTrue("up to and including S1E2" in prompt)
        assertTrue("About the series: A teacher cooks." in prompt)
        assertTrue(
            "Episodes up to the current one:\nS1E1 \"Episode e1\"\nS1E2 \"Episode e2\"" in prompt,
        )
        assertFalse("S2E1" in prompt)
        assertFalse("search the web" in prompt)
    }

    @Test
    fun `spoilers allowed drops the rule`() {
        val subject = ChatSubject.EpisodeSubject(episodes[1], series, episodes)

        val prompt = ChatContext.system(subject, null, avoidsSpoilers = false, searchesWeb = true)

        assertFalse("Never reveal" in prompt)
        assertTrue("You can search the web." in prompt)
        assertFalse("Search results often summarize" in prompt)
    }

    @Test
    fun `a watched movie gets no ending rule, an unwatched one does`() {
        val unwatched = ChatSubject.MovieSubject(
            testMovie("m", title = "Heat", genres = listOf("Crime")),
        )
        val watched = ChatSubject.MovieSubject(testMovie("m", isPlayed = true))

        val prompt = ChatContext.system(unwatched, null, avoidsSpoilers = true, searchesWeb = true)

        assertTrue("The user is watching the movie Heat (2020)." in prompt)
        assertTrue("Genres: Crime." in prompt)
        assertTrue("The user hasn't finished the movie." in prompt)
        assertTrue("prefer queries that won't return spoilers" in prompt)
        assertFalse(
            "hasn't finished" in
                ChatContext.system(watched, null, avoidsSpoilers = true, searchesWeb = false),
        )
    }

    @Test
    fun `the cast lists roles, then directors and writers`() {
        val cast =
            ChatCredits(
                actors = listOf(ChatCredit("Bryan Cranston", "Walter White")),
                guestStars = listOf(ChatCredit("Guest")),
                directors = listOf(ChatCredit("Vince Gilligan")),
                writers = listOf(ChatCredit("Peter Gould")),
            )

        val prompt =
            ChatContext.system(ChatSubject.MovieSubject(testMovie("m")), cast, false, false)

        val expected =
            "Cast: Bryan Cranston as Walter White; Guest\n" +
                "Directed by: Vince Gilligan\nWritten by: Peter Gould"
        assertTrue(expected in prompt)
    }
}
