package nl.jellycast.mobile.feature.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.SegmentType
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.mobile.core.remote.PairedTv
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.core.remote.RemoteSnapshot
import nl.jellycast.mobile.core.remote.RemoteTrack
import nl.jellycast.mobile.core.remote.RemoteTracks
import nl.jellycast.mobile.core.remote.SavedRemoteDevices
import nl.jellycast.mobile.core.remote.androidtv.PairingException
import nl.jellycast.mobile.core.remote.androidtv.RemoteClientInfo
import nl.jellycast.mobile.core.remote.androidtv.TvKey
import nl.jellycast.mobile.testing.FakeAndroidTvControl
import nl.jellycast.mobile.testing.FakeAndroidTvLink
import nl.jellycast.mobile.testing.FakeAndroidTvPairing
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakeRemoteDeviceStore
import nl.jellycast.mobile.testing.FakeRemoteSessionsApi
import nl.jellycast.mobile.testing.testRemoteDevice
import nl.jellycast.mobile.testing.testRemoteSession
import nl.jellycast.mobile.ui.info.InfoSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeRemoteSessionsApi()
    private val deviceStore = FakeRemoteDeviceStore()
    private val link = FakeAndroidTvLink()
    private val clock = Clock { 0 }

    private fun TestScope.remote() = RemoteSessions(api, deviceStore, link, clock, backgroundScope)

    @Test
    fun `the TV bar shows the most recent session and opens it`() = runTest {
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), emptyList())
        val remote = remote()
        val viewModel = TvBarViewModel(remote)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        val session = requireNotNull(viewModel.uiState.value)
        viewModel.onOpen(session)

        assertEquals("session-1", remote.state.value.shownSessionId)
    }

    @Test
    fun `the now-playing screen shows the library item, tracks and segments`() = runTest {
        val movie = testMovie("m1")
        val library = FakeLibraryRepository()
        val segment = MediaSegment(SegmentType.Intro, 0, 30_000)
        library.segments["m1"] = listOf(segment)
        api.tracks = RemoteTracks(listOf(RemoteTrack(1, "English")), emptyList())
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession(itemId = "m1")), emptyList())
        val remote = remote()
        remote.refresh()
        val viewModel =
            RemoteNowPlayingViewModel(
                remote,
                api,
                library,
                FakeLibraryStore(movies = listOf(movie)),
                FakeAndroidTvControl(),
                clock,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        assertFalse(viewModel.uiState.value.isOpen)

        remote.show(remote.state.value.sessions.single())
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(PlayableItem.MovieItem(movie), state.playable)
        assertEquals(InfoSubject.MovieInfo("m1"), state.infoSubject)
        assertEquals(listOf(segment), state.segments)
        assertEquals("English", state.tracks?.audio?.single()?.title)
        assertTrue(state.isControllable)
    }

    @Test
    fun `a stopped session stays on screen as stopped`() = runTest {
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), emptyList())
        val remote = remote()
        remote.refresh()
        val viewModel = nowPlaying(remote)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        remote.show(remote.state.value.sessions.single())
        runCurrent()

        api.snapshot = RemoteSnapshot(emptyList(), emptyList())
        remote.refresh()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertTrue(state.isStopped)
        assertFalse(state.isControllable)
    }

    @Test
    fun `a paired TV's volume keys go over the remote connection`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), emptyList())
        val remote = remote()
        remote.refresh()
        val control = FakeAndroidTvControl()
        val viewModel = nowPlaying(remote, control)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        remote.show(remote.state.value.sessions.single())
        runCurrent()

        assertTrue(viewModel.uiState.value.volume.isConnected)
        viewModel.onVolumeKey(TvKey.VolumeUp)
        viewModel.onVolumeKey(TvKey.VolumeDown)
        runCurrent()

        assertEquals(listOf(TvKey.VolumeUp, TvKey.VolumeDown), control.pressed)
        assertEquals(0L, viewModel.uiState.value.volume.changedAtMs)
    }

    @Test
    fun `continuing here stops the TV and closes the screen`() = runTest {
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), emptyList())
        val remote = remote()
        remote.refresh()
        val session = remote.state.value.sessions.single()
        remote.show(session)
        val viewModel = nowPlaying(remote)

        viewModel.onContinueHere(session)
        advanceTimeBy(BACKGROUND_WORK_MS)
        runCurrent()

        assertNull(remote.state.value.shownSessionId)
        assertEquals(RemoteCommand.Stop to "session-1", api.sent.first())
    }

    @Test
    fun `continuing here starts at the TV's position with its tracks, subtitles off when none`() {
        val episode = testEpisode("e1")
        val session = testRemoteSession(positionMs = 90_000, isPaused = true)

        val request = continueHereRequest(PlayableItem.EpisodeItem(episode), session, 0)

        assertEquals(90_000L, request.startPositionMs)
        assertEquals(StreamChoice(1, RemoteCommand.NO_STREAM), request.streams)
    }

    @Test
    fun `the play-on sheet lists open TVs and the ones that can be woken`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("sleeping", "10.0.0.2", "Bedroom")))
        link.reachableHosts += "10.0.0.2"
        api.snapshot = RemoteSnapshot(emptyList(), listOf(testRemoteDevice()))
        val viewModel = PlayOnViewModel(remote())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onOpen()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("device-1"), state.devices.map { it.deviceId })
        assertEquals(listOf("Bedroom"), state.wakeableTvs.map { it.name })
        assertEquals("device-1", state.pairableTv?.deviceId)
    }

    @Test
    fun `pairing keeps only hexadecimal characters of the code`() = runTest {
        val viewModel = pairing(remote(), FakeAndroidTvPairing())

        viewModel.onCodeChange("a1-b2 c3d4")

        assertEquals("A1B2C3", viewModel.uiState.value.code)
    }

    @Test
    fun `pairing with the right code stores the TV`() = runTest {
        val fake = FakeAndroidTvPairing()
        val remote = remote()
        val viewModel = pairing(remote, fake)

        viewModel.onStart(testRemoteDevice())
        runCurrent()
        assertEquals(PairingPhase.EnterCode, viewModel.uiState.value.phase)
        viewModel.onCodeChange("ABC123")
        viewModel.onPair()
        runCurrent()

        assertEquals(PairingPhase.Paired, viewModel.uiState.value.phase)
        assertEquals(listOf("ABC123"), fake.codes)
        assertEquals(listOf("device-1"), remote.state.value.paired.map { it.deviceId })
    }

    @Test
    fun `a wrong code asks to try again for a new one`() = runTest {
        val fake = FakeAndroidTvPairing().apply { failWith = PairingException.WrongCode() }
        val viewModel = pairing(remote(), fake)

        viewModel.onStart(testRemoteDevice())
        runCurrent()
        viewModel.onCodeChange("ABC123")
        viewModel.onPair()
        runCurrent()

        assertEquals(PairingPhase.Failed(PairingFailure.WrongCode), viewModel.uiState.value.phase)
    }

    @Test
    fun `a TV that doesn't answer says so`() = runTest {
        val fake = FakeAndroidTvPairing().apply { failBeforeCode = java.io.IOException("refused") }
        val viewModel = pairing(remote(), fake)

        viewModel.onStart(testRemoteDevice())
        runCurrent()

        assertEquals(PairingPhase.Failed(PairingFailure.NoAnswer), viewModel.uiState.value.phase)
    }

    @Test
    fun `pairing can't start before the code is complete`() = runTest {
        val fake = FakeAndroidTvPairing()
        val viewModel = pairing(remote(), fake)
        viewModel.onStart(testRemoteDevice())
        runCurrent()

        viewModel.onCodeChange("ABC")
        viewModel.onPair()
        runCurrent()

        assertEquals(PairingPhase.EnterCode, viewModel.uiState.value.phase)
        assertTrue(fake.codes.isEmpty())
    }

    @Test
    fun `the skip pill hides for the segment just skipped`() {
        val intro = MediaSegment(SegmentType.Intro, 0, 30_000)

        assertEquals(intro, skippableSegment(listOf(intro), 10_000, skippedStartMs = null))
        assertNull(skippableSegment(listOf(intro), 10_000, skippedStartMs = 0))
    }

    private fun nowPlaying(remote: RemoteSessions, control: FakeAndroidTvControl = FakeAndroidTvControl()) =
        RemoteNowPlayingViewModel(
            remote,
            api,
            FakeLibraryRepository(),
            FakeLibraryStore(),
            control,
            clock,
        )

    private fun pairing(remote: RemoteSessions, fake: FakeAndroidTvPairing) =
        PairTvViewModel(fake, remote, RemoteClientInfo("Pixel", "Google", "JellyCast Pixel AB12"))
}

// Background work isn't run by advanceUntilIdle; this covers the command's report delays.
private const val BACKGROUND_WORK_MS = 10_000L
