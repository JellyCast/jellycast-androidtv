package nl.jellycast.tv.feature.series

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.ShelfKind

sealed interface SeriesItem {
    val id: String

    /** Keyed by series: after watching it shows another episode but stays the focused card. */
    data class NextUp(val episode: Episode) : SeriesItem {
        override val id: String get() = episode.seriesId
    }

    data class Show(val series: Series) : SeriesItem {
        override val id: String get() = series.id
    }
}

data class SeriesRow(val key: String, val kind: ShelfKind, val items: List<SeriesItem>)

sealed interface SeriesUiState {
    data object Loading : SeriesUiState

    data object Error : SeriesUiState

    data class Success(
        val rows: List<SeriesRow>,
        val hero: SeriesItem?,
        val playRequest: PlayRequest? = null,
    ) : SeriesUiState
}
