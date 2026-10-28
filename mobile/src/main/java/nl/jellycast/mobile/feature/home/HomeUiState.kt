package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.NewItem

data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val syncProblem: SyncProblem? = null,
    val isRefreshing: Boolean = false,
    /** Items that can't play now: offline and not downloaded. */
    val unavailableIds: Set<String> = emptySet(),
)

sealed interface HomeContent {
    data object Loading : HomeContent

    /** No library yet and the first sync failed; [message] is `null` when the store gave none. */
    data class Failed(val message: String?, val isOffline: Boolean) : HomeContent

    data class Ready(val tiles: List<HomeTile>) : HomeContent
}

/** A cached library is shown, but the last refresh failed. */
data class SyncProblem(
    val message: String?,
    val syncedAtMs: Long,
    val isRetrying: Boolean,
    val isOffline: Boolean,
)

/** One tile of Home's grid; [contentId] is the movie or series it stands for. */
sealed interface HomeTile {
    val contentId: String

    /** The id that has to be downloaded for the tile to work offline. */
    val itemId: String

    /** A movie in progress or a series' next episode. */
    data class Continue(val item: PlayableItem) : HomeTile {
        override val contentId: String
            get() = when (item) {
                is PlayableItem.MovieItem -> item.movie.id
                is PlayableItem.EpisodeItem -> item.episode.seriesId
            }
        override val itemId: String get() = item.id
    }

    data class New(val item: NewItem) : HomeTile {
        override val contentId: String
            get() = when (item) {
                is NewItem.NewMovie -> item.movie.id
                is NewItem.NewSeries -> item.series.id
            }
        override val itemId: String get() = contentId
    }

    data class NotStarted(val series: Series, val seasonCount: Int, val episodeCount: Int) :
        HomeTile {
        override val contentId: String get() = series.id
        override val itemId: String get() = series.id
    }
}
