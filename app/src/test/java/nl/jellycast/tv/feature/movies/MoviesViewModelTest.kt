package nl.jellycast.tv.feature.movies

import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.common.HomeSectionLeaves
import nl.jellycast.tv.core.model.HomeSection
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.PlaybackTech
import nl.jellycast.tv.core.model.ShelfKind
import nl.jellycast.tv.core.model.UiPreferences
import nl.jellycast.tv.core.playback.PlaybackTechProvider
import nl.jellycast.tv.testing.FakeGenreNames
import nl.jellycast.tv.testing.FakeLibraryRepository
import nl.jellycast.tv.testing.FakeUiPreferencesRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.testMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoviesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val movies =
        listOf(
            testMovie("a", genres = listOf("Action"), addedAt = 1),
            testMovie("b", genres = listOf("Action"), resumePositionMs = 5, lastPlayedAt = 10),
            testMovie("c", genres = listOf("Action"), resumePositionMs = 5, lastPlayedAt = 20),
            testMovie("d", addedAt = 4),
        )

    @Test
    fun `shelves come in a fixed order`() {
        val shelves = buildMovieShelves(movies, FakeGenreNames())

        assertEquals(
            listOf(
                ShelfKind.ContinueWatching,
                ShelfKind.New,
                ShelfKind.Genre("Actie"),
            ),
            shelves.map { it.kind },
        )
        // Continue watching: last played first. New: everything unwatched, last added first.
        assertEquals(listOf("c", "b"), shelves[0].items.map { it.id })
        assertEquals(listOf("d", "a", "b", "c"), shelves[1].items.map { it.id })
    }

    @Test
    fun `new leaves out watched movies`() {
        val shelves =
            buildMovieShelves(
                listOf(testMovie("a", addedAt = 1), testMovie("b", addedAt = 2, isPlayed = true)),
                FakeGenreNames(),
            )

        assertEquals(listOf("a"), shelves.single { it.kind == ShelfKind.New }.items.map { it.id })
    }

    @Test
    fun `loads and switches shelves, clamped to the last shelf`() = runTest {
        val viewModel = viewModel(FakeLibraryRepository(movies = movies))
        viewModel.onShown()

        viewModel.onShelfChange(2)
        assertEquals(ShelfKind.Genre("Actie"), success(viewModel).shelf.kind)

        viewModel.onShelfChange(99)
        assertEquals(2, success(viewModel).shelfIndex)
    }

    @Test
    fun `refresh keeps the same shelf, even when a shelf before it disappears`() = runTest {
        val library = FakeLibraryRepository(movies = movies)
        val viewModel = viewModel(library)
        viewModel.onShown()
        viewModel.onShelfChange(2)

        // Nothing left to resume: Continue watching disappears and the genre moves to index 1.
        library.movies = movies.map { it.copy(resumePositionMs = 0) }
        viewModel.onShown()

        assertEquals(ShelfKind.Genre("Actie"), success(viewModel).shelf.kind)
        assertEquals(1, success(viewModel).shelfIndex)
    }

    @Test
    fun `play resumes at the progress, then the request is handled`() = runTest {
        val viewModel = viewModel(FakeLibraryRepository(movies = movies))
        viewModel.onShown()

        viewModel.onPlayClick(movies[1])
        assertEquals(PlayRequest("b", 5), success(viewModel).playRequest)

        viewModel.onPlayHandled()
        assertNull(success(viewModel).playRequest)

        viewModel.onPlayClick(movies[0])
        assertEquals(PlayRequest("a", 0), success(viewModel).playRequest)
    }

    @Test
    fun `error, then retry succeeds`() = runTest {
        val library = FakeLibraryRepository(movies = movies).apply { failNext = true }
        val viewModel = viewModel(library)

        viewModel.onShown()
        assertEquals(MoviesUiState.Error, viewModel.uiState.value)

        viewModel.onRetry()
        assertEquals(3, success(viewModel).shelves.size)
    }

    @Test
    fun `an empty library is an error, not an empty bookcase`() = runTest {
        val viewModel = viewModel(FakeLibraryRepository())

        viewModel.onShown()

        assertEquals(MoviesUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `the panel starts at the margin, then stays at a fixed position`() {
        // Spine plus gap = 48 px, panel 124 px right of the margin.
        assertEquals(0 to 0, bookcaseScrollPosition(index = 0, stepPx = 48, panelOffsetPx = 124))
        assertEquals(0 to 0, bookcaseScrollPosition(index = 2, stepPx = 48, panelOffsetPx = 124))
        assertEquals(0 to 20, bookcaseScrollPosition(index = 3, stepPx = 48, panelOffsetPx = 124))
        assertEquals(1 to 20, bookcaseScrollPosition(index = 4, stepPx = 48, panelOffsetPx = 124))
        assertEquals(7 to 20, bookcaseScrollPosition(index = 10, stepPx = 48, panelOffsetPx = 124))
    }

    @Test
    fun `view follows the preference`() = runTest {
        val preferences = FakeUiPreferencesRepository(UiPreferences(movieView = MovieView.Bookcase))
        val viewModel = viewModel(FakeLibraryRepository(movies = movies), preferences)
        assertEquals(MoviesUiState.Loading(MovieView.Bookcase), viewModel.uiState.value)

        viewModel.onShown()
        assertEquals(MovieView.Bookcase, success(viewModel).view)

        preferences.setMovieView(MovieView.Detail)
        assertEquals(MovieView.Detail, success(viewModel).view)
    }

    @Test
    fun `play from start plays from 0`() = runTest {
        val viewModel = viewModel(FakeLibraryRepository(movies = movies))
        viewModel.onShown()

        viewModel.onPlayFromStart(movies[1])

        assertEquals(PlayRequest("b", 0), success(viewModel).playRequest)
    }

    @Test
    fun `watched toggles on every shelf and reverts when the server fails`() = runTest {
        val library = FakeLibraryRepository(movies = movies)
        val viewModel = viewModel(library)
        viewModel.onShown()

        viewModel.onToggleWatched(movies[1])
        val updated = success(viewModel).shelves.flatMap { it.items }.filter { it.id == "b" }
        assertTrue(updated.isNotEmpty())
        assertTrue(updated.all { it.isPlayed && it.resumePositionMs == 0L })
        assertEquals(listOf("b" to true), library.playedCalls)

        library.failNext = true
        val played = success(viewModel).shelves.flatMap { it.items }.first { it.id == "b" }
        viewModel.onToggleWatched(played)
        assertTrue(success(viewModel).shelves.flatMap { it.items }.first { it.id == "b" }.isPlayed)
    }

    @Test
    fun `marking an in-progress movie unwatched clears its progress`() = runTest {
        val library = FakeLibraryRepository(movies = movies)
        val viewModel = viewModel(library)
        viewModel.onShown()
        val inProgress = movies.first { it.resumePositionMs > 0 && !it.isPlayed }

        viewModel.onSetWatched(inProgress, played = false)

        val updated = success(viewModel).shelves.flatMap { it.items }.filter {
            it.id ==
                inProgress.id
        }
        assertTrue(updated.all { !it.isPlayed && it.resumePositionMs == 0L })
        assertEquals(listOf(inProgress.id to false), library.playedCalls)
    }

    @Test
    fun `new holds all unwatched movies, old ones and more than 20, last added first`() {
        val many = List(25) { testMovie("m$it", addedAt = it.toLong()) } + testMovie("x")

        val new = buildMovieShelves(many, FakeGenreNames()).single { it.kind == ShelfKind.New }

        assertEquals(26, new.items.size)
        assertEquals("m24", new.items.first().id)
        assertEquals("x", new.items.last().id)
    }

    @Test
    fun `marking watched removes the movie from new and continue watching at once`() = runTest {
        val viewModel = viewModel(FakeLibraryRepository(movies = movies))
        viewModel.onShown()
        viewModel.onShelfChange(1)

        viewModel.onToggleWatched(movies[3])
        assertEquals(ShelfKind.New, success(viewModel).shelf.kind)
        assertEquals(listOf("a", "b", "c"), success(viewModel).shelf.items.map { it.id })

        // Continue watching becomes empty and disappears: New stays the current shelf.
        viewModel.onToggleWatched(movies[1])
        viewModel.onToggleWatched(movies[2])
        assertEquals(listOf(ShelfKind.New, ShelfKind.Genre("Actie")), kinds(viewModel))
        assertEquals(0, success(viewModel).shelfIndex)

        // New empty too: the shelf that moves into its place becomes current.
        viewModel.onToggleWatched(movies[0])
        assertEquals(ShelfKind.Genre("Actie"), success(viewModel).shelf.kind)
    }

    @Test
    fun `tech line is fetched once per movie`() = runTest {
        var calls = 0
        val tech = PlaybackTech(PlayMethod.DirectPlay, audio = null, replacedAudio = null)
        val viewModel =
            viewModel(FakeLibraryRepository(movies = movies), techProvider = {
                calls++
                tech
            })
        viewModel.onShown()

        viewModel.onMovieSettled(movies[0])
        viewModel.onMovieSettled(movies[0])

        assertEquals(1, calls)
        assertEquals(tech, success(viewModel).tech["a"])
        assertFalse("b" in success(viewModel).tech)
    }

    private fun viewModel(
        library: FakeLibraryRepository,
        preferences: FakeUiPreferencesRepository = FakeUiPreferencesRepository(),
        techProvider: PlaybackTechProvider = PlaybackTechProvider { null },
        sectionLeaves: HomeSectionLeaves = HomeSectionLeaves(),
    ) = MoviesViewModel(library, preferences, techProvider, FakeGenreNames(), sectionLeaves)

    @Test
    fun `leaving the section moves the backdrop of every movie on`() = runTest {
        val library = FakeLibraryRepository(movies = movies)
        val leaves = HomeSectionLeaves()
        val viewModel = viewModel(library, sectionLeaves = leaves)
        viewModel.onShown()

        leaves.onLeft(HomeSection.Library)
        assertTrue(library.advancedBackdrops.isEmpty())
        leaves.onLeft(HomeSection.Movies)

        assertEquals(listOf(listOf("a", "b", "c", "d")), library.advancedBackdrops)
    }

    @Test
    fun `a new backdrop shows while the section is hidden, without loading again`() = runTest {
        val library = FakeLibraryRepository(movies = movies)
        val viewModel = viewModel(library)
        viewModel.onShown()

        library.chosenBackdrops = mapOf("a" to "next")
        library.backdropChanges.value++

        val shown = success(viewModel).shelves.flatMap { it.items }.first { it.id == "a" }
        assertEquals("next", shown.artwork.backdrop)
        assertEquals(1, library.getMoviesCalls)
    }

    private fun kinds(viewModel: MoviesViewModel) = success(viewModel).shelves.map { it.kind }

    private fun success(viewModel: MoviesViewModel) =
        viewModel.uiState.value as MoviesUiState.Success
}
