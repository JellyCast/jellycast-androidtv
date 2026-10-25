package nl.jellycast.tv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.data.repository.LibraryRepository
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.MediaSegment
import nl.jellycast.tv.core.model.PlayableItem
import nl.jellycast.tv.core.model.PlaybackPlan
import nl.jellycast.tv.core.model.StreamChoice
import nl.jellycast.tv.core.model.nextEpisodeAfter
import nl.jellycast.tv.core.playback.PlaybackFailure
import nl.jellycast.tv.core.playback.PlaybackFallback
import nl.jellycast.tv.core.playback.PlaybackPlanner
import nl.jellycast.tv.core.playback.PlaybackRecovery
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.core.playback.recoverFrom
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber

data class PlayerInfo(
    val item: PlayableItem? = null,
    val nextEpisode: Episode? = null,
    val segments: List<MediaSegment> = emptyList(),
)

sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    data class Ready(val plan: PlaybackPlan, val info: PlayerInfo = PlayerInfo()) : PlayerUiState

    /** [detail] is the untranslated player message; `null` means the server did not respond. */
    data class Error(val detail: String?) : PlayerUiState
}

class PlayerViewModel(
    itemId: String,
    startPositionMs: Long,
    requestedStreams: StreamChoice,
    private val planner: PlaybackPlanner,
    private val settingsStore: PlaybackSettingsStore,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentItemId = itemId
    private var lastPositionMs = startPositionMs

    /** The tracks another device played, kept for every plan of this item. */
    private var requestedStreams = requestedStreams
    private var fallback = PlaybackFallback()

    private var retryUsed = false
    private var switchRetryUsed = false
    private var info = PlayerInfo()
    private var infoJob: Job? = null

    private var preparedNext: PlaybackPlan? = null
    private var prepareNextJob: Job? = null

    init {
        prepare()
        loadInfo()
    }

    /** Called when the app went to the background; the server has already ended a transcode. */
    fun onPlayerStopped(positionMs: Long) {
        lastPositionMs = positionMs
        prepare()
    }

    fun onPlaybackError(
        message: String,
        positionMs: Long,
        failure: PlaybackFailure,
        afterDisplaySwitch: Boolean = false,
        tunneled: Boolean = false,
    ) {
        // Right after a mode switch an error is often caused by the HDMI resync: retry the same
        // plan once without taking a fallback step.
        if (afterDisplaySwitch && !switchRetryUsed) {
            Timber.w("Playback error right after a mode switch (%s); retrying", message)
            switchRetryUsed = true
            lastPositionMs = positionMs
            prepare()
            return
        }
        val playMethod = (_uiState.value as? PlayerUiState.Ready)?.plan?.playMethod
        val recovery =
            playMethod?.let { recoverFrom(failure, fallback, it, retryUsed, tunneled) }
                ?: PlaybackRecovery.GiveUp
        Timber.w("Playback error %s (%s) → %s", failure, message, recovery)
        when (recovery) {
            PlaybackRecovery.Retry -> retryUsed = true

            is PlaybackRecovery.Fallback -> fallback = recovery.fallback

            PlaybackRecovery.GiveUp -> {
                _uiState.value = PlayerUiState.Error(message)
                return
            }
        }
        lastPositionMs = positionMs
        prepare()
    }

    /** The fallback stays: what failed once fails again for the same item. */
    fun onPlaybackStable() {
        retryUsed = false
    }

    fun onRetry() {
        retryUsed = false
        prepare()
    }

    /** Another item chosen on the phone replaces what plays. */
    fun onPlayItem(itemId: String, startPositionMs: Long, streams: StreamChoice) {
        switchTo(itemId, startPositionMs, PlayerInfo())
        requestedStreams = streams
        preparedNext = null
        prepareNextJob?.cancel()
        prepare()
        loadInfo()
    }

    fun onPlayNext() {
        val next = info.nextEpisode ?: return
        switchTo(next.id, next.startPositionMs(), PlayerInfo(item = PlayableItem.EpisodeItem(next)))
        val ready = preparedNext?.takeIf { it.itemId == next.id }
        preparedNext = null
        prepareNextJob?.cancel()
        if (ready != null) {
            _uiState.value = PlayerUiState.Ready(ready, info)
        } else {
            prepare()
        }
        loadInfo()
    }

    /** Wakes the server's disk and fetches the next plan; on failure [onPlayNext] fetches it. */
    fun onNearEnd() {
        val next = info.nextEpisode ?: return
        if (preparedNext?.itemId == next.id || prepareNextJob?.isActive == true) return
        prepareNextJob =
            viewModelScope.launch {
                library.warmUpMedia(next.id)
                preparedNext =
                    try {
                        planner.prepare(
                            next.id,
                            next.startPositionMs(),
                            settingsStore.current(),
                            PlaybackFallback(),
                            StreamChoice(null, null),
                        )
                    } catch (e: ApiClientException) {
                        Timber.w(e, "Prefetching the next episode failed")
                        null
                    }
            }
    }

    private fun switchTo(itemId: String, startPositionMs: Long, startInfo: PlayerInfo) {
        currentItemId = itemId
        lastPositionMs = startPositionMs
        requestedStreams = StreamChoice(null, null)
        fallback = PlaybackFallback()
        retryUsed = false
        switchRetryUsed = false
        info = startInfo
    }

    private fun Episode.startPositionMs(): Long = if (isPlayed) 0 else resumePositionMs

    private fun prepare() {
        _uiState.value = PlayerUiState.Loading
        val itemId = currentItemId
        viewModelScope.launch {
            _uiState.value =
                try {
                    PlayerUiState.Ready(
                        planner.prepare(
                            itemId,
                            lastPositionMs,
                            settingsStore.current(),
                            fallback,
                            requestedStreams,
                        ),
                        info,
                    )
                } catch (e: ApiClientException) {
                    Timber.w(e, "Fetching the playback plan failed")
                    PlayerUiState.Error(detail = null)
                }
        }
    }

    /** Title, next episode and segments; playback continues without them on failure. */
    private fun loadInfo() {
        infoJob?.cancel()
        val itemId = currentItemId
        infoJob =
            viewModelScope.launch {
                val segments = async { library.getSegments(itemId) }
                val item = library.getPlayable(itemId).getOrNull() ?: return@launch
                val next =
                    (item as? PlayableItem.EpisodeItem)?.episode?.let { episode ->
                        val episodes =
                            library.peekEpisodes(episode.seriesId)
                                ?: library.getEpisodes(episode.seriesId).getOrNull()
                        episodes?.let { nextEpisodeAfter(episode, it) }
                    }
                info = PlayerInfo(item, next, segments.await())
                _uiState.update { if (it is PlayerUiState.Ready) it.copy(info = info) else it }
            }
    }
}
