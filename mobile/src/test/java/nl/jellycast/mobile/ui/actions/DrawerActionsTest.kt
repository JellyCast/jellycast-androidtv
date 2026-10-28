package nl.jellycast.mobile.ui.actions

import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.settings.RatingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerActionsTest {
    private val movie =
        testMovie("m1", resumePositionMs = 60_000).copy(externalIds = ExternalIds(imdb = "tt1"))
    private val series = testSeries("s1").copy(externalIds = ExternalIds(tmdb = "77"))
    private val watched =
        testEpisode("e1", seriesId = "s1", number = 1, isPlayed = true, lastPlayedAt = 5)
    private val next = testEpisode("e2", seriesId = "s1", number = 2)
    private val index =
        LibraryIndex(LibrarySnapshot(listOf(movie), listOf(series), listOf(watched, next), 0), 0)

    private fun context(
        isOffline: Boolean = false,
        hasRemote: Boolean = false,
        source: RatingSource = RatingSource.Imdb,
        allowsNavigation: Boolean = true,
    ) = DrawerContext(index, isOffline, hasRemote, source, allowsNavigation)

    @Test
    fun `a movie in progress offers continue, from the start, both marks, download and its page`() {
        val actions = drawerActions(ActionTarget.MovieTarget(testMovie("m1")), context())

        assertEquals(
            listOf(
                DrawerAction.Play(PlayableItem.MovieItem(movie), isInProgress = true),
                DrawerAction.PlayFromStart(PlayableItem.MovieItem(movie)),
                DrawerAction.SetPlayed(listOf("m1"), played = true),
                DrawerAction.SetPlayed(listOf("m1"), played = false),
                DrawerAction.Download("m1"),
                DrawerAction.AddToList(listOf("m1")),
                DrawerAction.GoToMovie("m1"),
                DrawerAction.OpenOn("https://www.imdb.com/title/tt1/", RatingSource.Imdb),
            ),
            actions,
        )
    }

    @Test
    fun `a series plays its next episode and has no download`() {
        val actions = drawerActions(ActionTarget.SeriesTarget(series), context(hasRemote = true))

        assertEquals(
            DrawerAction.Play(PlayableItem.EpisodeItem(next), false, episodeLabel = next),
            actions.first(),
        )
        assertTrue(DrawerAction.PlayOn(PlayableItem.EpisodeItem(next)) in actions)
        assertTrue(actions.none { it is DrawerAction.Download })
        assertTrue(DrawerAction.GoToSeries("s1") in actions)
    }

    @Test
    fun `an episode links to TMDb under its series`() {
        val actions =
            drawerActions(ActionTarget.EpisodeTarget(next), context(source = RatingSource.Tmdb))

        assertTrue(
            DrawerAction.OpenOn(
                "https://www.themoviedb.org/tv/77/season/1/episode/2",
                RatingSource.Tmdb,
            ) in actions,
        )
    }

    @Test
    fun `offline hides marking and adding to a list`() {
        val actions = drawerActions(ActionTarget.MovieTarget(movie), context(isOffline = true))

        assertTrue(actions.none { it is DrawerAction.SetPlayed || it is DrawerAction.AddToList })
    }

    @Test
    fun `without navigation the page and site links are left out`() {
        val actions =
            drawerActions(ActionTarget.MovieTarget(movie), context(allowsNavigation = false))

        assertTrue(actions.none { it is DrawerAction.GoToMovie || it is DrawerAction.OpenOn })
    }

    @Test
    fun `a watched season only offers mark as not watched for its episodes`() {
        val season = ActionTarget.SeasonTarget(series, 1, listOf(watched))

        val actions = drawerActions(season, context())

        assertEquals(
            listOf(DrawerAction.SetPlayed(listOf("e1"), played = false)),
            actions.filterIsInstance<DrawerAction.SetPlayed>(),
        )
        assertTrue(actions.none { it is DrawerAction.Play })
    }
}
