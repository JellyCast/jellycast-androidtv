package nl.jellycast.mobile.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.continueEpisode

data class SeriesDetailUiState(
    val series: Series? = null,
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int? = null,
    val failed: Boolean = false,
) {
    val seasons: List<Int>
        get() = episodes.mapNotNull { it.seasonNumber }.distinct().sorted()

    val shownSeason: Int?
        get() = selectedSeason ?: seasons.firstOrNull()

    val seasonEpisodes: List<Episode>
        get() = episodes.filter { it.seasonNumber == shownSeason }

    /** `null` when nothing was started: then Play starts the first episode. */
    val continueWith: Episode?
        get() = continueEpisode(episodes)

    val firstEpisode: Episode?
        get() = episodes.firstOrNull { (it.seasonNumber ?: 0) > 0 } ?: episodes.firstOrNull()
}

class SeriesDetailViewModel(private val seriesId: String, private val library: LibraryRepository) :
    ViewModel() {
    private val _uiState =
        MutableStateFlow(
            SeriesDetailUiState(
                series = library.peekSeries(seriesId),
                episodes = library.peekEpisodes(seriesId).orEmpty(),
            ).withDefaultSeason(),
        )
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.update { it.copy(failed = false) }
        load()
    }

    fun onSeasonSelect(season: Int) {
        _uiState.update { it.copy(selectedSeason = season) }
    }

    private fun load() {
        viewModelScope.launch {
            val series = async { library.getSeriesById(seriesId) }
            val episodes = async { library.getEpisodes(seriesId) }
            val loadedSeries = series.await().getOrNull()
            val loadedEpisodes = episodes.await().getOrNull()
            _uiState.update {
                if (loadedSeries == null || loadedEpisodes == null) {
                    it.copy(failed = it.series == null)
                } else {
                    it.copy(series = loadedSeries, episodes = loadedEpisodes, failed = false)
                        .withDefaultSeason()
                }
            }
        }
    }
}

/** Opens on the season of the episode to continue with. */
private fun SeriesDetailUiState.withDefaultSeason(): SeriesDetailUiState =
    if (selectedSeason != null || episodes.isEmpty()) {
        this
    } else {
        copy(selectedSeason = (continueWith ?: firstEpisode)?.seasonNumber)
    }
