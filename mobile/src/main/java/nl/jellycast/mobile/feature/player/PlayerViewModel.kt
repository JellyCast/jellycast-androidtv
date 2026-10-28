package nl.jellycast.mobile.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.model.nextEpisodeAfter
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackFallback
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.playback.PlaybackRecovery
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.recoverFrom
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber

sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    data class Ready(
        val plan: PlaybackPlan,
        val item: PlayableItem? = null,
        val nextEpisode: Episode? = null,
        val segments: List<MediaSegment> = emptyList(),
    ) : PlayerUiState

    /** [detail] is the untranslated player message; `null` means the server did not respond. */
    data class Error(val detail: String?) : PlayerUiState
}

/** The fallback chain of the TV player, without its display mode and HDMI handling. */
class PlayerViewModel(
    itemId: String,
    startPositionMs: Long,
    private val planner: PlaybackPlanner,
    private val settingsStore: PlaybackSettingsStore,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentItemId = itemId
    private var lastPositionMs = startPositionMs
    private var fallback = PlaybackFallback()
    private var retryUsed = false
    private var item: PlayableItem? = null
    private var nextEpisode: Episode? = null
    private var segments: List<MediaSegment> = emptyList()

    init {
        prepare()
        loadInfo()
    }

    /** The app went to the background; the server has already ended a transcode. */
    fun onPlayerStopped(positionMs: Long) {
        lastPositionMs = positionMs
        prepare()
    }

    fun onPlaybackError(message: String, positionMs: Long, failure: PlaybackFailure) {
        val playMethod = (_uiState.value as? PlayerUiState.Ready)?.plan?.playMethod
        val recovery =
            playMethod?.let { recoverFrom(failure, fallback, it, retryUsed) }
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

    fun onPlaybackStable() {
        retryUsed = false
    }

    fun onRetry() {
        retryUsed = false
        prepare()
    }

    fun onPlayNext() {
        val next = nextEpisode ?: return
        currentItemId = next.id
        lastPositionMs = if (next.isPlayed) 0 else next.resumePositionMs
        fallback = PlaybackFallback()
        retryUsed = false
        item = PlayableItem.EpisodeItem(next)
        nextEpisode = null
        segments = emptyList()
        prepare()
        loadInfo()
    }

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
                            StreamChoice(null, null),
                        ),
                        item,
                        nextEpisode,
                        segments,
                    )
                } catch (e: ApiClientException) {
                    Timber.w(e, "Preparing playback failed")
                    PlayerUiState.Error(null)
                }
        }
    }

    private fun loadInfo() {
        val itemId = currentItemId
        viewModelScope.launch {
            segments = library.getSegments(itemId)
            val playable = library.getPlayable(itemId).getOrNull()
            item = playable ?: item
            nextEpisode =
                (playable as? PlayableItem.EpisodeItem)?.episode?.let { episode ->
                    library.getEpisodes(episode.seriesId).getOrNull()?.let {
                        nextEpisodeAfter(episode, it)
                    }
                }
            val ready = _uiState.value as? PlayerUiState.Ready ?: return@launch
            if (ready.plan.itemId == itemId) {
                _uiState.value =
                    ready.copy(item = item, nextEpisode = nextEpisode, segments = segments)
            }
        }
    }
}
