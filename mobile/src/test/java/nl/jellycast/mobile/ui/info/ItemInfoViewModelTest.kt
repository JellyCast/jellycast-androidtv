package nl.jellycast.mobile.ui.info

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.Credit
import nl.jellycast.core.model.CreditKind
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.MediaInfo
import nl.jellycast.core.model.MediaStreamInfo
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.core.model.SimilarMovie
import nl.jellycast.core.model.StreamKind
import nl.jellycast.core.testing.FakeGenreNames
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.testing.FakeAppPreferences
import nl.jellycast.mobile.testing.FakeDetailRepository
import nl.jellycast.mobile.testing.FakeLibraryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemInfoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fellowship =
        testMovie(
            "f",
            title = "The Lord of the Rings: The Fellowship of the Ring",
            genres = listOf("Action", "Adventure", "Drama"),
        )
            .copy(year = 2001, externalIds = ExternalIds(tmdbCollection = "lotr"))
    private val king =
        testMovie(
            "k",
            title = "The Lord of the Rings: The Return of the King",
        ).copy(year = 2003, externalIds = ExternalIds(tmdbCollection = "lotr"))
    private val towers =
        testMovie(
            "t",
            title = "The Lord of the Rings: The Two Towers",
        ).copy(year = 2002, externalIds = ExternalIds(tmdbCollection = "lotr"))
    private val hobbit = testMovie("h").copy(year = 2012)
    private val notInLibrary = "gone"

    private val repository =
        FakeDetailRepository(
            details =
                mapOf(
                    "f" to
                        ItemDetail(
                            credits = listOf(
                                credit("elijah", CreditKind.Actor),
                                credit("peter", CreditKind.Director),
                            ),
                            peopleIds = setOf("elijah", "ian", "peter"),
                            studioIds = setOf("wingnut"),
                            media = MediaInfo(
                                listOf(MediaStreamInfo(StreamKind.Video, height = 2160)),
                            ),
                        ),
                ),
            people = mapOf("elijah" to PersonDetails(biography = "Frodo.")),
            similar =
                mapOf(
                    "f" to
                        listOf(
                            SimilarMovie("t", setOf("elijah", "ian"), emptySet()),
                            SimilarMovie("h", setOf("ian", "peter"), setOf("wingnut")),
                            SimilarMovie(notInLibrary, setOf("ian", "peter"), setOf("wingnut")),
                        ),
                ),
        )

    @Test
    fun `movie shows its collection and the related movies not in it`() = runTest {
        val store = FakeLibraryStore(movies = listOf(fellowship, king, towers, hobbit))
        val viewModel = viewModel(InfoSubject.MovieInfo("f"), store)
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals("f", state.movie?.id)
        assertEquals(listOf("t", "k"), state.collection.map { it.id })
        assertEquals("The Lord of the Rings", state.collectionTitle)
        assertEquals(listOf("h"), state.related?.map { it.id })
        assertEquals("Actie, Adventure", state.genres)
        assertEquals(listOf(InfoTab.Cast, InfoTab.Related), state.tabs)
        assertEquals(2160, state.media?.streams?.single()?.height)
    }

    @Test
    fun `movie cast is grouped and gets person details`() = runTest {
        val viewModel =
            viewModel(InfoSubject.MovieInfo("f"), FakeLibraryStore(movies = listOf(fellowship)))
        collect(viewModel)

        val cast = (viewModel.uiState.value.cast as CastState.Loaded).credits
        assertEquals(listOf("elijah"), cast.actors.map { it.personId })
        assertEquals(listOf("peter"), cast.directors.map { it.personId })
        assertEquals("Frodo.", cast.details["elijah"]?.biography)
        assertEquals(listOf(listOf("elijah", "peter")), repository.personRequests)
    }

    @Test
    fun `a failed load shows the cast error and retry loads again`() = runTest {
        repository.failing += "f"
        val viewModel =
            viewModel(InfoSubject.MovieInfo("f"), FakeLibraryStore(movies = listOf(fellowship)))
        collect(viewModel)
        assertEquals(CastState.Failed, viewModel.uiState.value.cast)
        assertEquals(emptyList<String>(), viewModel.uiState.value.related?.map { it.id })

        repository.failing.clear()
        viewModel.onRetryCast()

        assertTrue(viewModel.uiState.value.cast is CastState.Loaded)
    }

    @Test
    fun `episode takes actors and producers from the series when it has none`() = runTest {
        repository.details =
            mapOf(
                "e1" to
                    ItemDetail(
                        listOf(
                            credit("dir", CreditKind.Director),
                            credit("guest", CreditKind.GuestStar),
                        ),
                        emptySet(),
                        emptySet(),
                        null,
                    ),
                "s" to
                    ItemDetail(
                        listOf(
                            credit("star", CreditKind.Actor),
                            credit("prod", CreditKind.Producer),
                        ),
                        emptySet(),
                        emptySet(),
                        null,
                    ),
            )
        val store =
            FakeLibraryStore(
                series = listOf(testSeries("s")),
                episodes = listOf(testEpisode("e1", seriesId = "s")),
            )
        val viewModel = viewModel(InfoSubject.EpisodeInfo("e1", "s"), store)
        collect(viewModel)

        val cast = (viewModel.uiState.value.cast as CastState.Loaded).credits
        assertEquals(listOf("star"), cast.actors.map { it.personId })
        assertEquals(listOf("prod"), cast.producers.map { it.personId })
        assertEquals(listOf("guest"), cast.guestStars.map { it.personId })
        assertEquals(listOf("dir"), cast.directors.map { it.personId })
        assertEquals(listOf(InfoTab.Episodes, InfoTab.Cast), viewModel.uiState.value.tabs)
    }

    @Test
    fun `episode window holds one before and six after, across seasons, without specials`() =
        runTest {
            val episodes =
                (1..5).map { testEpisode("a$it", seriesId = "s", season = 1, number = it) } +
                    (1..5).map { testEpisode("b$it", seriesId = "s", season = 2, number = it) } +
                    testEpisode("special", seriesId = "s", season = 0, number = 1)
            val store = FakeLibraryStore(series = listOf(testSeries("s")), episodes = episodes)
            val viewModel = viewModel(InfoSubject.EpisodeInfo("a4", "s"), store)
            collect(viewModel)

            assertEquals(
                listOf("a3", "a4", "a5", "b1", "b2", "b3", "b4", "b5"),
                viewModel.uiState.value.episodeWindow.map { it.id },
            )
        }

    @Test
    fun `series page continues with the next episode, else the first`() = runTest {
        val episodes =
            listOf(
                testEpisode("e1", seriesId = "s", number = 1, isPlayed = true, lastPlayedAt = 5),
                testEpisode("e2", seriesId = "s", number = 2),
            )
        val store =
            FakeLibraryStore(
                series = listOf(testSeries("s", genres = listOf("Crime"))),
                episodes = episodes,
            )
        val viewModel = viewModel(InfoSubject.SeriesInfo("s"), store)
        collect(viewModel)

        assertEquals("e2", viewModel.uiState.value.episode?.id)
        assertEquals("Misdaad", viewModel.uiState.value.genres)
        assertEquals(listOf("s"), repository.detailRequests)
    }

    @Test
    fun `the rating source follows the setting`() = runTest {
        val preferences = FakeAppPreferences()
        val viewModel =
            ItemInfoViewModel(
                InfoSubject.MovieInfo("f"),
                FakeLibraryStore(movies = listOf(fellowship)),
                repository,
                preferences,
                FakeGenreNames(),
            )
        collect(viewModel)

        preferences.setRatingSource(RatingSource.Tmdb)

        assertEquals(RatingSource.Tmdb, viewModel.uiState.value.ratingSource)
    }

    @Test
    fun `nothing shows before the library loaded`() = runTest {
        val store = FakeLibraryStore().apply { snapshot.value = null }
        val viewModel = viewModel(InfoSubject.MovieInfo("f"), store)
        collect(viewModel)

        assertNull(viewModel.uiState.value.movie)
    }

    private fun viewModel(subject: InfoSubject, store: FakeLibraryStore) =
        ItemInfoViewModel(subject, store, repository, FakeAppPreferences(), FakeGenreNames())

    private fun TestScope.collect(viewModel: ItemInfoViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun credit(id: String, kind: CreditKind) = Credit(id, id, null, kind, null)
}
