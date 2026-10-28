package nl.jellycast.mobile.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.data.repository.MediaSourceRepository
import nl.jellycast.core.data.repository.MediaSourceSummary
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.model.isFinishedAt
import nl.jellycast.core.model.nextEpisodeAfter
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackFallback
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.playback.PlaybackRecovery
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.TranscodePlanner
import nl.jellycast.core.playback.TranscodeTarget
import nl.jellycast.core.playback.recoverFrom
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.forNetwork
import nl.jellycast.mobile.core.settings.pick
import nl.jellycast.mobile.navigation.PlayQueue
import nl.jellycast.mobile.navigation.PlayRequest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber

/** What the quality sheet picks: Auto, or a fixed level. */
sealed interface QualityChoice {
    data object Auto : QualityChoice

    data class Level(val level: QualityLevel) : QualityChoice
}

/**
 * The one play of the app (iOS `NowPlaying` + `PlayerSession`), scoped to the Activity so it
 * outlives navigation. The engine composable owns the ExoPlayer and calls back here; every
 * server report goes through this class.
 */
class NowPlayingViewModel(
    private val planner: PlaybackPlanner,
    private val transcoder: TranscodePlanner,
    private val sources: MediaSourceRepository,
    private val settingsStore: PlaybackSettingsStore,
    private val library: LibraryRepository,
    private val store: LibraryStore,
    private val preferences: AppPreferences,
    private val connectivity: Connectivity,
    private val reports: PlaybackReports,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private var prepareJob: Job? = null
    private var infoJob: Job? = null
    private var lastLoadId = 0
    private var fallback = PlaybackFallback()
    private var retryUsed = false
    private var lastPositionMs = 0L
    private var source: MediaSourceSummary? = null

    /** Picked in the player; holds for the rest of the play, next items included. */
    private var pickedPreference: QualityPreference? = null

    /** Applied once, at the next start. */
    private var requestedStreams: StreamChoice? = null

    // Stopping an item decides "watched" with its own segments, also after the next one loaded.
    private val segmentsByItem = mutableMapOf<String, List<MediaSegment>>()

    fun onPlayRequest(request: PlayRequest) {
        val current = _uiState.value
        val queue =
            request.queue
                ?: current.queue?.let { queue ->
                    val position = queue.items.indexOfFirst { it.id == request.item.id }
                    queue.copy(position = position).takeIf { position >= 0 }
                }
        val sameItem =
            current.item?.id == request.item.id &&
                current.phase !is PlayerPhase.Failed &&
                request.startPositionMs == null &&
                request.streams == null
        _uiState.update { it.copy(isExpanded = true, queue = queue) }
        if (sameItem) {
            _uiState.update { it.copy(nextItem = nextItemFor(request.item, queue)) }
            return
        }
        val startMs =
            request.startPositionMs ?: if (request.fromStart) 0 else request.item.resumeStartMs()
        play(request.item, startMs, request.streams)
    }

    fun onMinimize() = _uiState.update { it.copy(isExpanded = false) }

    fun onExpand() = _uiState.update { it.copy(isExpanded = true) }

    fun onMiniPlayerSizeChange(large: Boolean) = _uiState.update {
        it.copy(isMiniPlayerLarge = large)
    }

    fun onClose() {
        prepareJob?.cancel()
        infoJob?.cancel()
        pickedPreference = null
        requestedStreams = null
        _uiState.update { NowPlayingUiState(isMiniPlayerLarge = it.isMiniPlayerLarge) }
    }

    fun onPlayNext() {
        val state = _uiState.value
        val next = state.nextItem ?: return
        val queue = state.queue?.let { queue ->
            queue.copy(position = queue.position + 1).takeIf { queue.next?.id == next.id }
        }
        _uiState.update { it.copy(queue = queue) }
        play(next, next.resumeStartMs(), streams = null)
    }

    fun onEngineStarted(load: PlaybackLoad) {
        reports.start(load.plan, load.plan.startPositionMs)
    }

    fun onProgress(load: PlaybackLoad, positionMs: Long, isPaused: Boolean, streams: StreamChoice?) {
        if (load != _uiState.value.load) return
        lastPositionMs = positionMs
        reports.progress(load.plan, positionMs, isPaused, streams)
    }

    /** Called once per load when its engine is released, whatever the reason. */
    fun onEngineStopped(
        load: PlaybackLoad,
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
        failed: Boolean,
    ) {
        val plan = load.plan
        val segments = segmentsByItem[plan.itemId].orEmpty()
        val finished = !failed && (ended || segments.isFinishedAt(positionMs, durationMs))
        reports.stop(plan, positionMs, failed = failed, finished = finished)
        store.recordPlayback(plan.itemId, positionMs, finished)
    }

    fun onEnded(load: PlaybackLoad) {
        if (load != _uiState.value.load) return
        if (_uiState.value.nextItem != null) onPlayNext() else onClose()
    }

    fun onPlaybackStable() {
        retryUsed = false
    }

    /** [beforeFirstFrame]: the file never showed a picture, so it "doesn't play here". */
    fun onPlaybackError(
        load: PlaybackLoad,
        failure: PlaybackFailure,
        positionMs: Long,
        beforeFirstFrame: Boolean,
    ) {
        if (load != _uiState.value.load) return
        lastPositionMs = positionMs
        val recovery = recoverFrom(failure, fallback, load.plan.playMethod, retryUsed)
        Timber.w("Playback error %s → %s", failure, recovery)
        when (recovery) {
            PlaybackRecovery.Retry -> retryUsed = true

            is PlaybackRecovery.Fallback -> fallback = recovery.fallback

            PlaybackRecovery.GiveUp -> {
                val quality = _uiState.value.quality
                val viaServer = quality?.lowerLevel?.takeIf { quality.level == QualityLevel.Original }
                val error = if (beforeFirstFrame) PlayerError.DoesNotPlay else PlayerError.StoppedMidPlay
                _uiState.update { it.copy(phase = PlayerPhase.Failed(error, viaServer)) }
                return
            }
        }
        prepare(positionMs)
    }

    fun onRetry() {
        retryUsed = false
        prepare(lastPositionMs)
    }

    fun onPlayViaServer() {
        val level = (_uiState.value.phase as? PlayerPhase.Failed)?.viaServer ?: return
        pickedPreference = QualityPreference.Fixed(level)
        prepare(lastPositionMs)
    }

    fun onQualityChosen(choice: QualityChoice, positionMs: Long, streams: StreamChoice?) {
        val preference =
            when (choice) {
                QualityChoice.Auto ->
                    networkPreference().takeIf { it is QualityPreference.Auto }
                        ?: QualityPreference.Auto()

                is QualityChoice.Level -> QualityPreference.Fixed(choice.level)
            }
        if (preference == _uiState.value.quality?.preference) return
        pickedPreference = preference
        requestedStreams = streams
        prepare(positionMs)
    }

    /** While transcoding, another track means a new transcode from the same position. */
    fun onTranscodeStreamsChosen(streams: StreamChoice, positionMs: Long) {
        requestedStreams = streams
        prepare(positionMs)
    }

    private fun play(item: PlayableItem, startMs: Long, streams: StreamChoice?) {
        fallback = PlaybackFallback()
        retryUsed = false
        source = null
        requestedStreams = streams
        segmentsByItem.keys.retainAll(setOfNotNull(_uiState.value.item?.id))
        _uiState.update {
            it.copy(
                item = item,
                segments = emptyList(),
                nextItem = nextItemFor(item, it.queue),
            )
        }
        loadInfo(item)
        prepare(startMs)
    }

    private fun loadInfo(item: PlayableItem) {
        infoJob?.cancel()
        infoJob =
            viewModelScope.launch {
                val segments = library.getSegments(item.id)
                segmentsByItem[item.id] = segments
                val next =
                    _uiState.value.nextItem
                        ?: nextFromServer(item).takeIf { _uiState.value.queue == null }
                _uiState.update {
                    if (it.item?.id != item.id) it else it.copy(segments = segments, nextItem = next)
                }
            }
    }

    private suspend fun nextFromServer(item: PlayableItem): PlayableItem? {
        val episode = (item as? PlayableItem.EpisodeItem)?.episode ?: return null
        val episodes = library.getEpisodes(episode.seriesId).getOrNull() ?: return null
        return nextEpisodeAfter(episode, episodes)?.let(PlayableItem::EpisodeItem)
    }

    private fun prepare(startMs: Long) {
        val item = _uiState.value.item ?: return
        prepareJob?.cancel()
        lastPositionMs = startMs
        _uiState.update { it.copy(phase = PlayerPhase.Preparing) }
        prepareJob =
            viewModelScope.launch {
                val summary = source ?: sources.getSourceSummary(item.id).also { source = it }
                val quality = qualityFor(summary)
                val streams = requestedStreams ?: StreamChoice(null, null)
                val phase =
                    try {
                        val plan = plan(item.id, startMs, quality.level, streams)
                        requestedStreams = null
                        PlayerPhase.Playing(PlaybackLoad(++lastLoadId, plan))
                    } catch (e: ApiClientException) {
                        Timber.w(e, "Preparing playback failed")
                        PlayerPhase.Failed(PlayerError.Server, viaServer = null)
                    } catch (e: IllegalStateException) {
                        Timber.w(e, "No playable source")
                        PlayerPhase.Failed(PlayerError.NoSource, viaServer = null)
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e, "Preparing playback failed")
                        PlayerPhase.Failed(PlayerError.DoesNotPlay, viaServer = null)
                    }
                _uiState.update { it.copy(phase = phase, quality = quality) }
            }
    }

    private suspend fun plan(
        itemId: String,
        startMs: Long,
        level: QualityLevel,
        streams: StreamChoice,
    ): PlaybackPlan {
        val settings = settingsStore.current()
        val maxBitrate = level.maxBitrate
        val maxWidth = level.maxWidth
        return if (maxBitrate == null || maxWidth == null) {
            planner.prepare(itemId, startMs, settings, fallback, streams)
        } else {
            transcoder.prepare(
                itemId,
                startMs,
                settings,
                TranscodeTarget(
                    maxBitrate = maxBitrate.toInt(),
                    maxWidth = maxWidth,
                    audioStreamIndex = streams.audioStreamIndex,
                    subtitleStreamIndex = streams.subtitleStreamIndex?.takeIf { it >= 0 },
                ),
            )
        }
    }

    private fun qualityFor(summary: MediaSourceSummary?): QualityState {
        val preference = pickedPreference ?: networkPreference()
        val bitrate = summary?.bitrate
        val width = summary?.width
        return QualityState(
            preference = preference,
            level = preference.pick(measuredBitsPerSecond = null, bitrate).resolvedFor(bitrate, width),
            offered = QualityLevel.offered(bitrate, width),
            sourceBitrate = bitrate,
        )
    }

    private fun networkPreference(): QualityPreference =
        preferences.quality.value.forNetwork(connectivity.networkKind.value)

    private fun nextItemFor(item: PlayableItem, queue: PlayQueue?): PlayableItem? {
        val snapshot = store.snapshot.value
        if (queue != null) return queue.next?.let { snapshot.fresh(it) }
        val episode = (item as? PlayableItem.EpisodeItem)?.episode ?: return null
        val episodes = snapshot?.episodesBySeries?.get(episode.seriesId) ?: return null
        return nextEpisodeAfter(episode, episodes)?.let(PlayableItem::EpisodeItem)
    }
}

/** In progress resumes; watched or not started plays from the start. */
internal fun PlayableItem.resumeStartMs(): Long = when (this) {
    is PlayableItem.MovieItem -> if (movie.isPlayed) 0 else movie.resumePositionMs
    is PlayableItem.EpisodeItem -> if (episode.isPlayed) 0 else episode.resumePositionMs
}

private fun LibrarySnapshot?.fresh(item: PlayableItem): PlayableItem {
    if (this == null) return item
    return when (item) {
        is PlayableItem.MovieItem -> moviesById[item.id]?.let(PlayableItem::MovieItem)
        is PlayableItem.EpisodeItem -> episodesById[item.id]?.let(PlayableItem::EpisodeItem)
    } ?: item
}
