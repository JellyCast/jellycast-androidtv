package nl.jellycast.tv.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.continueEpisode
import nl.jellycast.core.model.nextEpisodeAfter

/** Season [number] 0 holds the specials. */
data class Season(val number: Int, val episodes: List<Episode>) {
    val watchedCount: Int get() = episodes.count { it.isPlayed }
    val isWatched: Boolean get() = episodes.isNotEmpty() && watchedCount == episodes.size
    val key: String get() = "season-$number"

    // Built once here rather than per recomposition.
    val rowItems: List<SeasonRowItem> =
        episodes.map { SeasonRowItem.EpisodeItem(it) } + SeasonRowItem.MarkSeason(key)
}

sealed interface SeasonRowItem {
    val key: String

    data class EpisodeItem(val episode: Episode) : SeasonRowItem {
        override val key: String get() = episode.id
    }

    data class MarkSeason(val seasonKey: String) : SeasonRowItem {
        override val key: String get() = "mark-$seasonKey"
    }
}

data class SeriesDetailUiState(
    val series: Series? = null,
    /** `null` while the episodes are still loading. */
    val seasons: List<Season>? = null,
    val next: Episode? = null,
    val focusedEpisode: Episode? = null,
    val isError: Boolean = false,
    val playRequest: PlayRequest? = null,
) {
    val isWatched: Boolean
        get() = !seasons.isNullOrEmpty() && seasons.all { it.isWatched }

    val markableNext: Episode?
        get() = next?.takeIf { !it.isPlayed && seasons != null }

    /** Where Continue moves after marking: the following episode, or back to the first. */
    internal val nextAfterMark: Episode?
        get() {
            val next = markableNext ?: return null
            val all = seasons.orEmpty().flatMap { it.episodes }
            val following =
                nextEpisodeAfter(next, all) ?: all.firstOrNull { (it.seasonNumber ?: 0) > 0 }
            return following?.takeIf { it.id != next.id }
        }

    val markingNextHidesButton: Boolean
        get() = markableNext != null && nextAfterMark?.isPlayed != false

    val isUnstarted: Boolean
        get() =
            seasons.orEmpty().all { it.watchedCount == 0 } &&
                (next?.resumePositionMs ?: 0) == 0L
}

class SeriesDetailViewModel(private val seriesId: String, private val library: LibraryRepository) :
    ViewModel() {
    private val _uiState =
        MutableStateFlow(
            SeriesDetailUiState(
                series = library.peekSeries(seriesId),
                seasons = library.peekEpisodes(seriesId)?.let { groupSeasons(it) },
                next = library.peekEpisodes(seriesId)?.let { continueOrFirst(it) },
            ),
        )
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var focusJob: Job? = null

    // While a write is in flight, an older load result must not overwrite the local state.
    private var writesInFlight = 0

    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.update { it.copy(isError = false) }
        load()
    }

    fun onFocusEpisode(episode: Episode) {
        focusJob?.cancel()
        if (_uiState.value.focusedEpisode?.id == episode.id) return
        focusJob =
            viewModelScope.launch {
                delay(HERO_DELAY_MS)
                _uiState.update { it.copy(focusedEpisode = episode) }
                library.warmUpMedia(episode.id)
            }
    }

    fun onFocusContinue() {
        focusJob?.cancel()
        _uiState.update { it.copy(focusedEpisode = null) }
    }

    fun onContinueClick() {
        val next = _uiState.value.next ?: return
        _uiState.update { it.copy(playRequest = PlayRequest(next.id, next.resumePositionMs)) }
    }

    fun onEpisodeClick(episode: Episode) {
        val start = if (episode.isPlayed) 0 else episode.resumePositionMs
        _uiState.update { it.copy(playRequest = PlayRequest(episode.id, start)) }
    }

    fun onPlayHandled() {
        _uiState.update { it.copy(playRequest = null) }
    }

    fun onMarkNextClick() {
        val state = _uiState.value
        val next = state.markableNext ?: return
        val following = state.nextAfterMark
        markPlayed(next.id, played = true, episodeIds = setOf(next.id)) {
            it.copy(next = following?.withPlayed(setOf(next.id), played = true))
        }
    }

    fun onSeriesWatchedToggle() {
        val state = _uiState.value
        val seasons = state.seasons ?: return
        val played = !state.isWatched
        markPlayed(seriesId, played, seasons.flatMap { it.episodes }.map { it.id }.toSet())
    }

    fun onSeasonWatchedToggle(seasonKey: String) {
        val season = _uiState.value.seasons?.firstOrNull { it.key == seasonKey } ?: return
        val played = !season.isWatched
        val ids = season.episodes.map { it.id }.toSet()
        val seasonId = season.episodes.firstNotNullOfOrNull { it.seasonId }
        if (seasonId != null) {
            markPlayed(seasonId, played, ids)
        } else {
            // Specials without a season item have to be marked per episode.
            ids.forEach { markPlayed(it, played, setOf(it)) }
        }
    }

    /** Updates locally first, then the server; the reload fixes Continue and failed writes. */
    private fun markPlayed(
        targetId: String,
        played: Boolean,
        episodeIds: Set<String>,
        alsoUpdate: (SeriesDetailUiState) -> SeriesDetailUiState = { it },
    ) {
        _uiState.update { state ->
            alsoUpdate(
                state.copy(
                    seasons = state.seasons?.map { it.withPlayed(episodeIds, played) },
                    next = state.next?.withPlayed(episodeIds, played),
                    focusedEpisode = state.focusedEpisode?.withPlayed(episodeIds, played),
                ),
            )
        }
        writesInFlight++
        viewModelScope.launch {
            library.setPlayed(targetId, played)
            writesInFlight--
            if (writesInFlight == 0) load(force = true)
        }
    }

    private fun load(force: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (!force) return
            loadJob?.cancel()
        }
        loadJob =
            viewModelScope.launch {
                val loaded =
                    withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                        coroutineScope {
                            val series = async { library.getSeriesById(seriesId) }
                            val episodes = async { library.getEpisodes(seriesId) }
                            series.await() to episodes.await()
                        }
                    }
                val episodes = loaded?.second?.getOrNull()
                if (writesInFlight > 0) return@launch
                _uiState.update { current ->
                    if (loaded == null || episodes == null) {
                        return@update current.copy(isError = current.seasons == null)
                    }
                    current.copy(
                        series = loaded.first.getOrNull() ?: current.series,
                        seasons = groupSeasons(episodes),
                        next = continueOrFirst(episodes),
                        focusedEpisode =
                            current.focusedEpisode?.let { old ->
                                episodes.firstOrNull { it.id == old.id }
                            },
                        isError = false,
                    )
                }
                _uiState.value.next?.let { library.warmUpMedia(it.id) }
            }
    }
}

private fun Season.withPlayed(episodeIds: Set<String>, played: Boolean): Season =
    if (episodes.none { it.id in episodeIds }) {
        this
    } else {
        copy(episodes = episodes.map { it.withPlayed(episodeIds, played) })
    }

private fun Episode.withPlayed(ids: Set<String>, played: Boolean): Episode =
    if (id in ids) copy(isPlayed = played, resumePositionMs = 0) else this

private fun continueOrFirst(episodes: List<Episode>): Episode? =
    continueEpisode(episodes) ?: episodes
        .filter { (it.seasonNumber ?: 0) > 0 }
        .minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber ?: Int.MAX_VALUE }))

/** Seasons from 1 upwards, specials (season 0 or none) last. */
internal fun groupSeasons(episodes: List<Episode>): List<Season> = episodes
    .groupBy { it.seasonNumber ?: 0 }
    .map { (number, list) -> Season(number, list.sortedBy { it.episodeNumber ?: Int.MAX_VALUE }) }
    .sortedWith(compareBy<Season> { it.number == 0 }.thenBy { it.number })
