package nl.jellycast.tv.feature.player

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.PlayMethod.DirectPlay
import nl.jellycast.core.model.PlayMethod.DirectStream
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.model.nextEpisodeAfter
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackFallback
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.FakePlaybackSettingsStore
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noStreams = StreamChoice(null, null)
    private val requests = mutableListOf<Pair<String, Long>>()
    private val fallbacks = mutableListOf<PlaybackFallback>()
    private val planner =
        PlaybackPlanner { itemId, start, _, fallback, _ ->
            requests += itemId to start
            fallbacks += fallback
            testPlan(
                itemId,
                start,
                playMethod = if (fallback.directPlay) DirectPlay else DirectStream,
            )
        }

    private val e1 = testEpisode("e1", "s1", season = 1, number = 1)
    private val e2 = testEpisode("e2", "s1", season = 1, number = 2, resumePositionMs = 90_000)
    private val special = testEpisode("x", "s1", season = 0, number = 1)
    private val library = FakeLibraryRepository(episodes = mapOf("s1" to listOf(e2, special, e1)))
    private val movies = FakeLibraryRepository(movies = listOf(testMovie("m1")))

    @Test
    fun `an episode gets its title and next episode`() = runTest {
        val viewModel =
            PlayerViewModel("e1", 0, noStreams, planner, FakePlaybackSettingsStore(), library)

        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(PlayableItem.EpisodeItem(e1), state.info.item)
        assertEquals(e2, state.info.nextEpisode)
    }

    @Test
    fun `the next episode plays from its saved position`() = runTest {
        val viewModel =
            PlayerViewModel("e1", 0, noStreams, planner, FakePlaybackSettingsStore(), library)

        viewModel.onPlayNext()

        assertEquals("e2" to 90_000L, requests.last())
        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("e2", state.plan.itemId)
        assertNull("last episode: no next", state.info.nextEpisode)
    }

    @Test
    fun `a movie has no next episode`() = runTest {
        val movie = testMovie("m1")
        val viewModel =
            PlayerViewModel(
                "m1",
                0,
                noStreams,
                planner,
                FakePlaybackSettingsStore(),
                FakeLibraryRepository(movies = listOf(movie)),
            )

        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(PlayableItem.MovieItem(movie), state.info.item)
        assertNull(state.info.nextEpisode)
    }

    @Test
    fun `the next episode continues into the next season and skips specials`() {
        val s2e1 = testEpisode("s2e1", "s1", season = 2, number = 1)
        val all = listOf(s2e1, special, e2, e1)

        assertEquals(e2, nextEpisodeAfter(e1, all))
        assertEquals(s2e1, nextEpisodeAfter(e2, all))
        assertNull(nextEpisodeAfter(s2e1, all))
    }

    @Test
    fun `a source error continues with a remux from the same position`() = runTest {
        val viewModel =
            PlayerViewModel("m1", 0, noStreams, planner, FakePlaybackSettingsStore(), movies)

        viewModel.onPlaybackError("broken", 60_000, PlaybackFailure.Source)

        assertEquals(PlaybackFallback(directPlay = false), fallbacks.last())
        assertEquals("m1" to 60_000L, requests.last())
        assertTrue(viewModel.uiState.value is PlayerUiState.Ready)
    }

    @Test
    fun `an audio error decodes locally, then stereo, then shows the error screen`() = runTest {
        val viewModel =
            PlayerViewModel("m1", 0, noStreams, planner, FakePlaybackSettingsStore(), movies)

        viewModel.onPlaybackError("sink", 0, PlaybackFailure.Audio)
        assertEquals(PlaybackFallback(passthrough = false), fallbacks.last())
        viewModel.onPlaybackError("sink", 0, PlaybackFailure.Audio)
        assertEquals(PlaybackFallback(passthrough = false, stereo = true), fallbacks.last())
        viewModel.onPlaybackError("sink", 0, PlaybackFailure.Audio)

        assertEquals(PlayerUiState.Error("sink"), viewModel.uiState.value)
    }

    @Test
    fun `a network error retries once, and again after stable playback`() = runTest {
        val viewModel =
            PlayerViewModel("m1", 0, noStreams, planner, FakePlaybackSettingsStore(), movies)

        viewModel.onPlaybackError("net", 10_000, PlaybackFailure.Network)
        assertEquals(PlaybackFallback(), fallbacks.last())
        assertEquals(2, requests.size)
        viewModel.onPlaybackStable()
        viewModel.onPlaybackError("net", 20_000, PlaybackFailure.Network)
        assertEquals(3, requests.size)
        viewModel.onPlaybackError("net", 30_000, PlaybackFailure.Network)

        assertEquals(PlayerUiState.Error("net"), viewModel.uiState.value)
    }

    @Test
    fun `the next episode starts without a fallback`() = runTest {
        val viewModel =
            PlayerViewModel("e1", 0, noStreams, planner, FakePlaybackSettingsStore(), library)
        viewModel.onPlaybackError("broken", 0, PlaybackFailure.Source)

        viewModel.onPlayNext()

        assertEquals(PlaybackFallback(), fallbacks.last())
    }

    @Test
    fun `an error right after a mode switch retries the same plan once, without fallback`() =
        runTest {
            val viewModel =
                PlayerViewModel("m1", 0, noStreams, planner, FakePlaybackSettingsStore(), movies)

            viewModel.onPlaybackError(
                "gone",
                5_000,
                PlaybackFailure.Video,
                afterDisplaySwitch = true,
            )
            assertEquals(PlaybackFallback(), fallbacks.last())
            assertEquals(2, requests.size)

            // The second time counts as a normal error: video retries first.
            viewModel.onPlaybackError(
                "gone",
                5_000,
                PlaybackFailure.Video,
                afterDisplaySwitch = true,
            )
            assertEquals(3, requests.size)
            assertEquals(PlaybackFallback(), fallbacks.last())
            viewModel.onPlaybackError(
                "gone",
                5_000,
                PlaybackFailure.Video,
                afterDisplaySwitch = true,
            )
            assertEquals(PlaybackFallback(directPlay = false, videoCopy = false), fallbacks.last())
        }

    @Test
    fun `the credits prepare the next episode, which then starts without waiting`() = runTest {
        val viewModel =
            PlayerViewModel("e1", 0, noStreams, planner, FakePlaybackSettingsStore(), library)

        viewModel.onNearEnd()
        assertEquals("e2" to 90_000L, requests.last())
        assertEquals(listOf("e2"), library.warmedUp)
        val asked = requests.size

        viewModel.onPlayNext()
        assertEquals("no second request", asked, requests.size)
        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("e2", state.plan.itemId)
    }

    @Test
    fun `without a prepared plan the next episode fetches its own`() = runTest {
        val viewModel =
            PlayerViewModel("e1", 0, noStreams, planner, FakePlaybackSettingsStore(), library)
        val before = requests.size

        viewModel.onPlayNext()

        assertEquals(before + 1, requests.size)
        assertEquals("e2", (viewModel.uiState.value as PlayerUiState.Ready).plan.itemId)
    }
}
