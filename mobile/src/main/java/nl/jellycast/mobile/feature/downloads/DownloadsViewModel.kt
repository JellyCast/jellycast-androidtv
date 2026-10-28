package nl.jellycast.mobile.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.EpisodeOrder
import nl.jellycast.mobile.core.downloads.VolumeSpace
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.network.Connectivity

data class ActiveRow(val download: Download, val secondsLeft: Double?)

/** How far a downloaded movie is watched, from the library, which knows what was watched since. */
sealed interface MovieWatch {
    data object Watched : MovieWatch

    data class InProgress(val remainingMs: Long) : MovieWatch

    data object NotWatched : MovieWatch
}

/** One movie, or all downloaded episodes of one series. */
sealed interface DownloadGroup {
    val id: String
    val downloads: List<Download>
    val bytes: Long get() = downloads.sumOf { it.bytes }

    data class MovieGroup(val download: Download, val item: PlayableItem, val watch: MovieWatch) :
        DownloadGroup {
        override val id: String get() = download.id
        override val downloads: List<Download> get() = listOf(download)
    }

    data class SeriesGroup(
        override val id: String,
        val name: String,
        override val downloads: List<Download>,
        val unwatchedCount: Int,
    ) : DownloadGroup
}

data class StorageSummary(
    val movieCount: Int,
    val episodeCount: Int,
    val usedBytes: Long,
    val space: VolumeSpace?,
) {
    val otherBytes: Long?
        get() = space?.let { (it.totalBytes - it.freeBytes - usedBytes).coerceAtLeast(0) }
}

data class DownloadsUiState(
    val active: List<ActiveRow> = emptyList(),
    val groups: List<DownloadGroup> = emptyList(),
    val storage: StorageSummary = StorageSummary(0, 0, 0, null),
    val isEmpty: Boolean = true,
    val canPauseAll: Boolean = false,
    val isOffline: Boolean = false,
)

class DownloadsViewModel(
    private val store: DownloadStore,
    library: LibraryStore,
    connectivity: Connectivity,
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> =
        combine(
            store.downloads,
            store.rate,
            store.space,
            library.snapshot,
            connectivity.isOffline,
        ) { downloads, rate, space, snapshot, isOffline ->
            val active =
                downloads.filter {
                    it.status.isActive ||
                        it.status == DownloadStatus.Paused ||
                        it.status is DownloadStatus.Failed
                }
            val finished = downloads.filter { it.status == DownloadStatus.Done }
            val movies = finished.count { !it.media.isEpisode }
            DownloadsUiState(
                active = active.map { ActiveRow(it, store.secondsLeft(it, rate)) },
                groups = groups(finished, snapshot),
                storage =
                    StorageSummary(
                        movieCount = movies,
                        episodeCount = finished.size - movies,
                        usedBytes = downloads.sumOf { it.bytes },
                        space = space,
                    ),
                isEmpty = downloads.isEmpty(),
                canPauseAll = active.any { it.status.isActive },
                isOffline = isOffline,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun onPauseAll() = store.pauseAll()

    fun onRemove(download: Download) = store.remove(download.id)

    /** Removing a whole series also stops keeping it ahead. */
    fun onRemoveGroup(group: DownloadGroup) {
        group.downloads.forEach { store.remove(it.id) }
        if (group is DownloadGroup.SeriesGroup) store.settings.stopKeeping(group.id)
    }
}

/** Most recently added first; a series sorts by its newest episode. */
internal fun groups(finished: List<Download>, snapshot: LibrarySnapshot?): List<DownloadGroup> {
    val movies =
        finished.filter { !it.media.isEpisode }.map { download ->
            val movie = snapshot?.moviesById?.get(download.id)
            val watch =
                when {
                    movie == null -> MovieWatch.NotWatched

                    movie.isPlayed -> MovieWatch.Watched

                    movie.resumePositionMs > 0 ->
                        remainingMs(movie.resumePositionMs, movie.runtimeMs)
                            ?.let { MovieWatch.InProgress(it) } ?: MovieWatch.NotWatched

                    else -> MovieWatch.NotWatched
                }
            val item = movie?.let { PlayableItem.MovieItem(it) } ?: download.media.toPlayable()
            DownloadGroup.MovieGroup(download, item, watch) to download.addedAtMs
        }
    val series =
        finished.filter { it.media.isEpisode }.groupBy { it.media.seriesId.orEmpty() }
            .map { (seriesId, episodes) ->
                val sorted = episodes.sortedWith(compareBy(EpisodeOrder) { it.media })
                val unwatched =
                    sorted.count { snapshot?.episodesById?.get(it.id)?.isPlayed != true }
                val name =
                    snapshot?.seriesById?.get(seriesId)?.title
                        ?: sorted.first().media.seriesTitle.orEmpty()
                DownloadGroup.SeriesGroup(seriesId, name, sorted, unwatched) to
                    episodes.maxOf { it.addedAtMs }
            }
    return (movies + series).sortedByDescending { it.second }.map { it.first }
}
