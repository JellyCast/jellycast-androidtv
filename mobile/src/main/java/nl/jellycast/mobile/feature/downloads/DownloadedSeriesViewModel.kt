package nl.jellycast.mobile.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.EpisodeOrder
import nl.jellycast.mobile.core.library.LibraryStore

/** The library's copy of the episode when it has one, so its watch state is current. */
data class DownloadedEpisode(val episode: Episode, val download: Download)

data class DownloadedSeriesUiState(
    val seriesName: String = "",
    val episodes: List<DownloadedEpisode> = emptyList(),
    val hasSeriesPage: Boolean = false,
)

class DownloadedSeriesViewModel(
    private val seriesId: String,
    private val store: DownloadStore,
    library: LibraryStore,
) : ViewModel() {
    val uiState: StateFlow<DownloadedSeriesUiState> =
        combine(store.downloads, library.snapshot) { downloads, snapshot ->
            val episodes =
                downloads
                    .filter { it.status == DownloadStatus.Done && it.media.seriesId == seriesId }
                    .sortedWith(compareBy(EpisodeOrder) { it.media })
                    .mapNotNull { download ->
                        val saved = download.media.toPlayable() as? PlayableItem.EpisodeItem
                        val episode =
                            snapshot?.episodesById?.get(download.id)
                                ?: saved?.episode
                                ?: return@mapNotNull null
                        DownloadedEpisode(episode, download)
                    }
            val series = snapshot?.seriesById?.get(seriesId)
            DownloadedSeriesUiState(
                seriesName =
                    series?.title ?: episodes.firstOrNull()?.episode?.seriesTitle.orEmpty(),
                episodes = episodes,
                hasSeriesPage = series != null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadedSeriesUiState())

    fun onRemove(itemId: String) = store.remove(itemId)
}
