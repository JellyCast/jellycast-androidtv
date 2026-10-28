package nl.jellycast.mobile.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SeriesProgress
import nl.jellycast.mobile.core.library.SeriesStatus
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.library.progressOf
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.RatingSource

sealed interface SeriesUiState {
    /** The very first sync has not delivered a library yet. */
    data object Loading : SeriesUiState

    /** The series, its episodes or a playable episode is missing. */
    data object Missing : SeriesUiState

    data class Ready(val page: SeriesPage) : SeriesUiState
}

/** What the main button says it plays. */
enum class PlayKind { Continue, WatchAgain, Play }

data class SeasonSummary(val number: Int, val episodes: List<Episode>) {
    val episodeCount: Int get() = episodes.size
    val watchedCount: Int get() = episodes.count { it.isPlayed }
    val isWatched: Boolean get() = episodes.isNotEmpty() && episodes.all { it.isPlayed }
    val isSpecials: Boolean get() = number == 0
}

data class SeriesPage(
    val series: Series,
    /** What the main button plays: the next episode, or the first once all are watched. */
    val next: Episode,
    val playKind: PlayKind,
    /** The episode the progress says comes next, outlined in the list; `null` when none. */
    val nextEpisodeId: String?,
    val isWatched: Boolean,
    /** Without Specials. */
    val seasonCount: Int,
    val seasons: List<SeasonSummary>,
    val selectedSeason: SeasonSummary,
    val isOffline: Boolean,
    val ratingSource: RatingSource,
)

class SeriesViewModel(
    private val seriesId: String,
    private val store: LibraryStore,
    connectivity: Connectivity,
    preferences: AppPreferences,
) : ViewModel() {
    private val chosenSeason = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<SeriesUiState> =
        combine(
            store.snapshot,
            store.syncState,
            chosenSeason,
            connectivity.isOffline,
            preferences.ratingSource,
        ) { snapshot, sync, season, offline, source ->
            when {
                snapshot != null ->
                    page(snapshot, season, offline, source)?.let(SeriesUiState::Ready)
                        ?: SeriesUiState.Missing

                sync is SyncState.Failed -> SeriesUiState.Missing

                else -> SeriesUiState.Loading
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SeriesUiState.Loading,
        )

    fun onSelectSeason(number: Int) {
        chosenSeason.value = number
    }

    fun onToggleSeriesWatched() {
        val page = (uiState.value as? SeriesUiState.Ready)?.page ?: return
        viewModelScope.launch { store.setPlayed(listOf(seriesId), !page.isWatched) }
    }

    fun onRetry() {
        viewModelScope.launch { store.refresh() }
    }

    private fun page(
        snapshot: LibrarySnapshot,
        chosenSeason: Int?,
        isOffline: Boolean,
        ratingSource: RatingSource,
    ): SeriesPage? {
        val series = snapshot.seriesById[seriesId] ?: return null
        val progress = progressOf(series, snapshot.episodesBySeries[seriesId].orEmpty())
        val next = progress.next ?: progress.episodes.firstOrNull { (it.seasonNumber ?: 0) > 0 }
        if (next == null) return null
        val seasons =
            progress.seasons.map { number ->
                SeasonSummary(number, progress.episodes.filter { (it.seasonNumber ?: 0) == number })
            }
        val defaultSeason = progress.next?.seasonNumber ?: progress.seasons.firstOrNull()
        val selected =
            seasons.firstOrNull { it.number == chosenSeason }
                ?: seasons.firstOrNull { it.number == defaultSeason }
                ?: seasons.first()
        return SeriesPage(
            series = series,
            next = next,
            playKind = playKind(progress, next),
            nextEpisodeId = progress.next?.id,
            isWatched = progress.status == SeriesStatus.Watched,
            seasonCount = seasons.count { !it.isSpecials },
            seasons = seasons,
            selectedSeason = selected,
            isOffline = isOffline,
            ratingSource = ratingSource,
        )
    }

    private fun playKind(progress: SeriesProgress, next: Episode): PlayKind = when {
        !next.isPlayed && next.resumePositionMs > 0 -> PlayKind.Continue
        progress.next == null && progress.status == SeriesStatus.Watched -> PlayKind.WatchAgain
        else -> PlayKind.Play
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
