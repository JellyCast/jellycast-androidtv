package nl.jellycast.mobile.feature.series

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SeriesDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val episodes =
        listOf(
            testEpisode(
                "e1",
                seriesId = "s1",
                season = 1,
                number = 1,
                isPlayed = true,
                lastPlayedAt = 10,
            ),
            testEpisode("e2", seriesId = "s1", season = 2, number = 1),
            testEpisode("e3", seriesId = "s1", season = 2, number = 2),
        )

    private fun library() =
        FakeLibraryRepository(series = listOf(testSeries("s1")), episodes = mapOf("s1" to episodes))

    @Test
    fun `opens on the season of the episode to continue with`() = runTest {
        val state = SeriesDetailViewModel("s1", library()).uiState.value

        assertEquals("e2", state.continueWith?.id)
        assertEquals(2, state.shownSeason)
        assertEquals(listOf("e2", "e3"), state.seasonEpisodes.map { it.id })
    }

    @Test
    fun `choosing a season shows its episodes`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library())

        viewModel.onSeasonSelect(1)

        assertEquals(listOf("e1"), viewModel.uiState.value.seasonEpisodes.map { it.id })
    }

    @Test
    fun `an unstarted series offers the first episode`() = runTest {
        val fresh = listOf(testEpisode("e1", seriesId = "s1", season = 1, number = 1))
        val library =
            FakeLibraryRepository(
                series = listOf(testSeries("s1")),
                episodes = mapOf("s1" to fresh),
            )

        val state = SeriesDetailViewModel("s1", library).uiState.value

        assertNull(state.continueWith)
        assertEquals("e1", state.firstEpisode?.id)
    }
}
