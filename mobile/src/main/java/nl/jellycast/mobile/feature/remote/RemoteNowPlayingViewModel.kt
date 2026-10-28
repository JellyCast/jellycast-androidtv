package nl.jellycast.mobile.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.core.remote.RemoteState
import nl.jellycast.mobile.core.remote.RemoteTracks
import nl.jellycast.mobile.core.remote.RemoteSessionsApi
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvControl
import nl.jellycast.mobile.core.remote.androidtv.TvKey
import nl.jellycast.mobile.core.remote.androidtv.TvVolume
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.ui.info.InfoSubject

data class TvVolumeState(
    val isConnected: Boolean = false,
    val volume: TvVolume? = null,
    /** The last press, so the level shows for a moment. */
    val changedAtMs: Long? = null,
)

data class RemoteNowPlayingUiState(
    val isOpen: Boolean = false,
    /** The live session, or the last one seen once it stopped. */
    val session: RemoteSession? = null,
    val isStopped: Boolean = false,
    /** The item in the library: the info panel and "Continue on this phone" need it. */
    val playable: PlayableItem? = null,
    val tracks: RemoteTracks? = null,
    val segments: List<MediaSegment> = emptyList(),
    val volume: TvVolumeState = TvVolumeState(),
    val commandFailed: Boolean = false,
    val waking: String? = null,
) {
    val isControllable: Boolean get() = session?.canControl == true && !isStopped

    val infoSubject: InfoSubject?
        get() = when (val item = playable) {
            is PlayableItem.MovieItem -> InfoSubject.MovieInfo(item.movie.id)
            is PlayableItem.EpisodeItem ->
                InfoSubject.EpisodeInfo(item.episode.id, item.episode.seriesId)
            null -> null
        }
}

private data class ItemDetails(
    val itemId: String? = null,
    val tracks: RemoteTracks? = null,
    val segments: List<MediaSegment> = emptyList(),
)

/** The full-screen "playing on the TV" screen, over the whole app. */
class RemoteNowPlayingViewModel(
    private val remote: RemoteSessions,
    private val api: RemoteSessionsApi,
    private val library: LibraryRepository,
    private val store: LibraryStore,
    private val control: AndroidTvControl,
    private val clock: Clock,
) : ViewModel() {
    private val details = MutableStateFlow(ItemDetails())
    private val volume = MutableStateFlow(TvVolumeState())
    private val pressLock = Mutex()
    private var lastSeen: RemoteSession? = null

    val uiState: StateFlow<RemoteNowPlayingUiState> =
        channelFlow {
            launch { shownItemIds().collectLatest(::loadDetails) }
            launch { pairedHosts().collectLatest(::keepVolume) }
            combine(remote.state, store.snapshot, details, volume, ::build).collect { send(it) }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MS),
            RemoteNowPlayingUiState(),
        )

    private fun build(
        state: RemoteState,
        snapshot: LibrarySnapshot?,
        details: ItemDetails,
        volume: TvVolumeState,
    ): RemoteNowPlayingUiState {
        val live = state.session(state.shownSessionId)
        if (state.shownSessionId == null) lastSeen = null else if (live != null) lastSeen = live
        val session = live ?: lastSeen
        return RemoteNowPlayingUiState(
            isOpen = state.shownSessionId != null && session != null,
            session = session,
            isStopped = live == null,
            playable = session?.let { snapshot?.playable(it.item.id) },
            tracks = details.tracks.takeIf { details.itemId == session?.item?.id },
            segments = if (details.itemId == session?.item?.id) details.segments else emptyList(),
            volume = volume,
            commandFailed = state.commandFailed,
            waking = state.waking,
        )
    }

    private fun shownItemIds() =
        remote.state.map { it.session(it.shownSessionId)?.item?.id }.distinctUntilChanged()

    /** A paired Chromecast that takes commands: its volume can be set over the remote. */
    private fun pairedHosts() =
        remote.state.map { state ->
            state.session(state.shownSessionId)
                ?.takeIf { it.canControl }
                ?.let { state.pairedTv(it.deviceId)?.host }
        }.distinctUntilChanged()

    private suspend fun loadDetails(itemId: String?) {
        if (itemId == null || itemId == details.value.itemId) return
        details.value = ItemDetails(itemId)
        val segments = library.getSegments(itemId)
        val tracks = api.getTracks(itemId).getOrNull()
        details.value = ItemDetails(itemId, tracks, segments)
    }

    /** Keeps the connection open, reconnecting after a drop, while this TV is shown. */
    private suspend fun keepVolume(host: String?) {
        volume.value = TvVolumeState()
        if (host == null) return
        while (true) {
            try {
                control.run(
                    host,
                    onReady = { volume.update { it.copy(isConnected = true) } },
                    onVolume = { level -> volume.update { it.copy(volume = level) } },
                )
            } catch (_: IOException) {
                // Dropped or not reachable; try again in a moment.
            } catch (_: GeneralSecurityException) {
                // The TLS setup failed; try again in a moment.
            }
            volume.update { it.copy(isConnected = false) }
            delay(VOLUME_RETRY_MS)
        }
    }

    fun onCommand(command: RemoteCommand) {
        val session = uiState.value.session ?: return
        remote.launchSend(command, session.id)
    }

    /** Presses go out in order, also when the keys are pressed quickly. */
    fun onVolumeKey(key: TvKey) {
        if (!volume.value.isConnected) return
        volume.update { it.copy(changedAtMs = clock.nowMs()) }
        viewModelScope.launch {
            pressLock.withLock {
                try {
                    control.press(key)
                } catch (_: IOException) {
                    volume.update { it.copy(isConnected = false) }
                }
            }
        }
    }

    fun onClose() = remote.hideNowPlaying()

    /** Stops the TV and turns a paired Chromecast off; the caller plays on here. */
    fun onContinueHere(session: RemoteSession) {
        remote.hideNowPlaying()
        remote.launchHandBack(session)
    }

    /** Another TV took it over: this one stops, and the TV bar follows the new one. */
    fun onHandedOver(session: RemoteSession) {
        remote.hideNowPlaying()
        remote.launchSend(RemoteCommand.Stop, session.id)
    }

    fun onDismissCommandFailure() = remote.dismissCommandFailure()

    private companion object {
        const val STOP_AFTER_MS = 5_000L
        const val VOLUME_RETRY_MS = 3_000L
    }
}

private fun LibrarySnapshot.playable(itemId: String): PlayableItem? =
    moviesById[itemId]?.let { PlayableItem.MovieItem(it) }
        ?: episodesById[itemId]?.let { PlayableItem.EpisodeItem(it) }

/** Plays here from where the TV was, with the TV's audio and subtitles. */
fun continueHereRequest(playable: PlayableItem, session: RemoteSession, nowMs: Long) = PlayRequest(
    item = playable,
    streams =
        StreamChoice(
            session.audioStreamIndex,
            session.subtitleStreamIndex ?: RemoteCommand.NO_STREAM,
        ),
    startPositionMs = session.positionAt(nowMs),
)
