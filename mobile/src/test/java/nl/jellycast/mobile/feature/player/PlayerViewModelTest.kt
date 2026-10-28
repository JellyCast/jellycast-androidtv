package nl.jellycast.mobile.feature.player

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackFallback
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.FakePlaybackSettingsStore
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fallbacks = mutableListOf<PlaybackFallback>()

    private val planner =
        PlaybackPlanner { itemId, start, _, fallback, _ ->
            fallbacks += fallback
            testPlan(itemId = itemId, startPositionMs = start, playMethod = PlayMethod.DirectPlay)
        }

    private val library =
        FakeLibraryRepository(
            episodes =
                mapOf(
                    "s1" to
                        listOf(
                            testEpisode("e1", seriesId = "s1", number = 1),
                            testEpisode(
                                "e2",
                                seriesId = "s1",
                                number = 2,
                                resumePositionMs = 5_000,
                            ),
                        ),
                ),
        )

    private fun viewModel(itemId: String = "e1") =
        PlayerViewModel(itemId, 1_000, planner, FakePlaybackSettingsStore(), library)

    @Test
    fun `prepares the plan from the start position and knows the next episode`() = runTest {
        val state = viewModel().uiState.value as PlayerUiState.Ready

        assertEquals(1_000L, state.plan.startPositionMs)
        assertEquals("e2", state.nextEpisode?.id)
    }

    @Test
    fun `next episode resumes where it was left`() = runTest {
        val viewModel = viewModel()

        viewModel.onPlayNext()

        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("e2", state.plan.itemId)
        assertEquals(5_000L, state.plan.startPositionMs)
    }

    @Test
    fun `a file error steps down the fallback chain from the same position`() = runTest {
        val viewModel = viewModel()

        viewModel.onPlaybackError("bad file", 42_000, PlaybackFailure.Source)

        val state = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(42_000L, state.plan.startPositionMs)
        assertTrue(fallbacks.last() != PlaybackFallback())
    }

    @Test
    fun `an error that keeps coming back ends on the error screen`() = runTest {
        val viewModel = viewModel()

        repeat(MAX_ERRORS) {
            viewModel.onPlaybackError("decoder", 0, PlaybackFailure.Video)
        }

        assertTrue(viewModel.uiState.value is PlayerUiState.Error)
    }
}

private const val MAX_ERRORS = 10
