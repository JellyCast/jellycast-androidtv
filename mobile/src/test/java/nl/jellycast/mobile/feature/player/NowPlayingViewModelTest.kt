package nl.jellycast.mobile.feature.player

import java.io.File
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.data.repository.MediaSourceSummary
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.SegmentType
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.playback.TranscodePlanner
import nl.jellycast.core.playback.TranscodeTarget
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.FakePlaybackSettingsStore
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testPlan
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.navigation.PlayQueue
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.PlayerTestConnectivity
import nl.jellycast.mobile.testing.PlayerTestPreferences
import nl.jellycast.mobile.testing.PlayerTestSources
import nl.jellycast.mobile.testing.RecordingPlaybackReports
import nl.jellycast.mobile.ui.downloads.LocalDownloads
import nl.jellycast.mobile.ui.downloads.OfflinePositions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val directStarts = mutableListOf<Pair<String, Long>>()
    private val transcodes = mutableListOf<Pair<String, TranscodeTarget>>()

    private val planner =
        PlaybackPlanner { itemId, start, _, _, _ ->
            directStarts += itemId to start
            testPlan(itemId = itemId, startPositionMs = start, playMethod = PlayMethod.DirectPlay)
        }
    private val transcoder =
        TranscodePlanner { itemId, start, _, target ->
            transcodes += itemId to target
            testPlan(itemId = itemId, startPositionMs = start, playMethod = PlayMethod.Transcode)
        }

    private val e1 = testEpisode("e1", seriesId = "s1", number = 1)
    private val e2 = testEpisode("e2", seriesId = "s1", number = 2, resumePositionMs = 5_000)
    private val store = FakeLibraryStore(episodes = listOf(e1, e2))
    private val library = FakeLibraryRepository()
    private val reports = RecordingPlaybackReports()
    private val sources = PlayerTestSources()
    private val connectivity = PlayerTestConnectivity(NetworkKind.Local)

    private val downloads = mutableMapOf<String, File>()
    private val offline = mutableListOf<Triple<String, Long, Boolean>>()

    private fun viewModel() = NowPlayingViewModel(
        planner = planner,
        transcoder = transcoder,
        sources = sources,
        settingsStore = FakePlaybackSettingsStore(),
        library = library,
        store = store,
        preferences = PlayerTestPreferences(),
        connectivity = connectivity,
        reports = reports,
        localDownloads = LocalDownloads { downloads[it] },
        offlinePositions = OfflinePositions { id, position, finished ->
            offline += Triple(id, position, finished)
        },
    )

    private fun NowPlayingViewModel.load(): PlaybackLoad =
        requireNotNull(uiState.value.load) { "nothing loaded" }

    @Test
    fun `a play request expands and starts an in-progress item where it was left`() = runTest {
        val viewModel = viewModel()

        viewModel.onPlayRequest(PlayRequest(PlayableItem.EpisodeItem(e2)))

        assertTrue(viewModel.uiState.value.isExpanded)
        assertEquals("e2" to 5_000L, directStarts.single())
    }

    @Test
    fun `from the start and watched items start at zero`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.EpisodeItem(e2), fromStart = true))
        viewModel.onPlayRequest(
            PlayRequest(
                PlayableItem.MovieItem(testMovie("m1", resumePositionMs = 9_000, isPlayed = true)),
            ),
        )

        assertEquals(listOf("e2" to 0L, "m1" to 0L), directStarts)
    }

    @Test
    fun `the next episode comes from the library and plays at the end`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.EpisodeItem(e1)))
        assertEquals("e2", viewModel.uiState.value.nextItem?.id)

        viewModel.onEnded(viewModel.load())

        assertEquals("e2", viewModel.uiState.value.item?.id)
        assertEquals("e2" to 5_000L, directStarts.last())
    }

    @Test
    fun `the end without a next item closes the player`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        viewModel.onEnded(viewModel.load())

        assertNull(viewModel.uiState.value.item)
    }

    @Test
    fun `a list plays its next item and keeps its position`() = runTest {
        val movies = listOf(testMovie("a"), testMovie("b"), testMovie("c"))
        val queue = PlayQueue("l1", "Favourites", movies.map(PlayableItem::MovieItem), position = 0)
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(queue.items[0], queue = queue))
        assertEquals("b", viewModel.uiState.value.nextItem?.id)

        viewModel.onPlayNext()

        assertEquals(1, viewModel.uiState.value.queue?.position)
        assertEquals("c", viewModel.uiState.value.nextItem?.id)
    }

    @Test
    fun `tapping an item outside the list drops the list`() = runTest {
        val queue = PlayQueue("l1", "List", listOf(PlayableItem.MovieItem(testMovie("a"))), 0)
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(queue.items[0], queue = queue))

        viewModel.onPlayRequest(PlayRequest(PlayableItem.EpisodeItem(e1)))

        assertNull(viewModel.uiState.value.queue)
        assertEquals("e2", viewModel.uiState.value.nextItem?.id)
    }

    @Test
    fun `stopping in the credits counts as watched`() = runTest {
        library.segments["m1"] = listOf(MediaSegment(SegmentType.Outro, 6_000_000, 7_200_000))
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))
        val load = viewModel.load()

        viewModel.onEngineStopped(load, 6_100_000, 7_200_000, ended = false, failed = false)
        viewModel.onEngineStopped(load, 1_000_000, 7_200_000, ended = false, failed = false)

        assertEquals(listOf(true, false), reports.stops.map { it.finished })
        assertEquals(Triple("m1", 6_100_000L, true), store.recorded.first())
    }

    @Test
    fun `a failed stop is never marked watched`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        viewModel.onEngineStopped(viewModel.load(), 0, 0, ended = true, failed = true)

        assertFalse(reports.stops.single().finished)
    }

    @Test
    fun `mobile data defaults to a server transcode up to 1080p`() = runTest {
        connectivity.networkKind.value = NetworkKind.Cellular
        sources.summary = MediaSourceSummary(bitrate = 40_000_000, width = 3840)
        val viewModel = viewModel()

        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        val target = transcodes.single().second
        assertEquals(5_000_000, target.maxBitrate)
        assertEquals(1920, target.maxWidth)
        assertEquals(QualityLevel.P1080, viewModel.uiState.value.quality?.level)
    }

    @Test
    fun `a level that would not shrink the file plays the original`() = runTest {
        connectivity.networkKind.value = NetworkKind.Cellular
        sources.summary = MediaSourceSummary(bitrate = 3_000_000, width = 1280)
        val viewModel = viewModel()

        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        assertTrue(transcodes.isEmpty())
        assertEquals(QualityLevel.Original, viewModel.uiState.value.quality?.level)
    }

    @Test
    fun `a quality picked in the player replays from the position and carries over`() = runTest {
        sources.summary = MediaSourceSummary(bitrate = 40_000_000, width = 3840)
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.EpisodeItem(e1)))

        viewModel.onQualityChosen(QualityChoice.Level(QualityLevel.P720), 60_000, streams = null)
        viewModel.onPlayNext()

        assertEquals(listOf("e1", "e2"), transcodes.map { it.first })
        assertEquals(
            QualityPreference.Fixed(QualityLevel.P720),
            viewModel.uiState.value.quality?.preference,
        )
    }

    @Test
    fun `choosing the quality that already plays does not reload`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        viewModel.onQualityChosen(QualityChoice.Level(QualityLevel.Original), 1_000, streams = null)

        assertEquals(1, directStarts.size)
    }

    @Test
    fun `a file that never played offers to play via the server`() = runTest {
        sources.summary = MediaSourceSummary(bitrate = 40_000_000, width = 3840)
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))

        viewModel.onPlaybackError(
            viewModel.load(),
            PlaybackFailure.Other,
            0,
            beforeFirstFrame = true,
        )
        val failed = viewModel.uiState.value.phase as PlayerPhase.Failed
        viewModel.onPlayViaServer()

        assertEquals(PlayerError.DoesNotPlay, failed.error)
        assertEquals(QualityLevel.P1440, failed.viaServer)
        assertEquals(10_000_000, transcodes.single().second.maxBitrate)
    }

    @Test
    fun `an error mid-play retries from the failed position`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))
        viewModel.onPlaybackError(
            viewModel.load(),
            PlaybackFailure.Other,
            42_000,
            beforeFirstFrame = false,
        )
        assertEquals(
            PlayerError.StoppedMidPlay,
            (viewModel.uiState.value.phase as PlayerPhase.Failed).error,
        )

        viewModel.onRetry()

        assertEquals("m1" to 42_000L, directStarts.last())
    }

    @Test
    fun `closing keeps the mini player size for the next play`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))
        viewModel.onMiniPlayerSizeChange(large = true)

        viewModel.onClose()

        assertNull(viewModel.uiState.value.item)
        assertTrue(viewModel.uiState.value.isMiniPlayerLarge)
    }

    @Test
    fun `a download plays from its file without server reports`() = runTest {
        downloads["m1"] = File("/data/m1/compact.m3u8")
        val viewModel = viewModel()
        viewModel.onPlayRequest(PlayRequest(PlayableItem.MovieItem(testMovie("m1"))))
        val load = viewModel.load()

        viewModel.onEngineStarted(load)
        viewModel.onEngineStopped(load, 30_000, 7_200_000, ended = false, failed = false)

        assertTrue(load.isLocal)
        assertTrue(load.plan.streamUrl.endsWith("compact.m3u8"))
        assertTrue(directStarts.isEmpty())
        assertTrue(reports.started.isEmpty() && reports.stops.isEmpty())
        assertEquals(Triple("m1", 30_000L, false), offline.single())
    }
}
