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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.core.common.Clock
import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.GenreNames
import nl.jellycast.core.model.HomeSection
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.ShelfKind
import nl.jellycast.core.model.buildShelves
import nl.jellycast.core.model.newShelfItems

const val HERO_DELAY_MS = 300L

class SeriesViewModel(
    private val library: LibraryRepository,
    private val genreNames: GenreNames,
    sectionLeaves: HomeSectionLeaves,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var heroJob: Job? = null
    private var series: List<Series> = emptyList()
    private var nextUp: List<Episode> = emptyList()

    init {
        viewModelScope.launch {
            sectionLeaves.leavesOf(HomeSection.Series).collect {
                library.advanceBackdrops(series.map { it.id } + nextUp.map { it.seriesId })
            }
        }
        viewModelScope.launch {
            library.backdropChanges.drop(1).collect {
                if (_uiState.value !is SeriesUiState.Success) return@collect
                series = series.map(library::withBackdrop)
                nextUp = nextUp.map(library::withBackdrop)
                showRows()
            }
        }
    }

    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.value = SeriesUiState.Loading
        load()
    }

    fun onFocusItem(item: SeriesItem) {
        heroJob?.cancel()
        val alreadyHero = (_uiState.value as? SeriesUiState.Success)?.hero == item
        heroJob =
            viewModelScope.launch {
                delay(HERO_DELAY_MS)
                if (!alreadyHero) updateSuccess { it.copy(hero = item) }
                when (item) {
                    is SeriesItem.Show -> library.prefetchEpisodes(item.series.id)
                    is SeriesItem.NextUp -> library.warmUpMedia(item.episode.id)
                }
            }
    }

    fun onEpisodeClick(episode: Episode) {
        updateSuccess { it.copy(playRequest = PlayRequest(episode.id, episode.resumePositionMs)) }
    }

    fun onPlayHandled() {
        updateSuccess { it.copy(playRequest = null) }
    }

    // The server decides what Continue watching shows next, so the rows reload afterwards.
    fun onSetEpisodePlayed(episode: Episode, played: Boolean) {
        viewModelScope.launch {
            library.setPlayed(episode.id, played)
            load(force = true)
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
                            val series = async { library.getSeries() }
                            val nextUp = async { library.getNextUp() }
                            series.await().getOrNull() to nextUp.await().getOrNull()
                        }
                    }
                val loadedSeries = loaded?.first
                val loadedNextUp = loaded?.second
                if (loadedSeries == null || loadedNextUp == null) {
                    _uiState.update { if (it is SeriesUiState.Success) it else SeriesUiState.Error }
                    return@launch
                }
                series = loadedSeries
                nextUp = loadedNextUp
                showRows()
            }
    }

    private fun showRows() {
        val rows = buildSeriesRows(series, nextUp, genreNames, clock.nowMs())
        _uiState.update { current ->
            val old = current as? SeriesUiState.Success
            SeriesUiState.Success(
                rows = rows,
                hero = old?.hero?.let { rows.findItem(it.id) } ?: rows.firstItem(),
                playRequest = old?.playRequest,
            )
        }
    }

    private fun updateSuccess(transform: (SeriesUiState.Success) -> SeriesUiState.Success) {
        _uiState.update { if (it is SeriesUiState.Success) transform(it) else it }
    }
}

internal fun buildSeriesRows(
    series: List<Series>,
    nextUp: List<Episode>,
    genreNames: GenreNames,
    nowMs: Long,
): List<SeriesRow> {
    val shows = series.map { SeriesItem.Show(it) }
    val withNewEpisodes =
        newShelfItems(
            shows,
            nowMs,
            addedAt = { it.series.lastEpisodeAddedAt },
            isPlayed = { it.series.isPlayed },
        )
    val inProgressIds = nextUp.mapTo(mutableSetOf()) { it.seriesId }
    val notStarted =
        shows.filter { !it.series.hasPlayedEpisodes && it.series.id !in inProgressIds }
    val shelves =
        buildShelves(
            continueWatching = nextUp.map { SeriesItem.NextUp(it) },
            new = withNewEpisodes,
            all = shows,
            genreNames = genreNames,
            notStarted = notStarted,
            genresOf = { (it as? SeriesItem.Show)?.series?.genres.orEmpty() },
        )
    return shelves.map { shelf ->
        SeriesRow(key = shelf.kind.key(), kind = shelf.kind, items = shelf.items)
    }
}

private fun ShelfKind.key(): String = when (this) {
    ShelfKind.ContinueWatching -> "continue"
    ShelfKind.New -> "new"
    ShelfKind.NotStarted -> "not-started"
    is ShelfKind.Genre -> "genre-$name"
}

internal fun List<SeriesRow>.firstItem(): SeriesItem? = firstOrNull()?.items?.firstOrNull()

internal fun List<SeriesRow>.findItem(id: String): SeriesItem? =
    firstNotNullOfOrNull { row -> row.items.firstOrNull { it.id == id } }
