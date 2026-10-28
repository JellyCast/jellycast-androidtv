package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.NewEntry

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

    data class Ready(val rows: HomeRows) : HomeContent
}

/** A cached library is shown, but the last refresh failed. */
data class SyncProblem(
    val message: String?,
    val syncedAtMs: Long,
    val isRetrying: Boolean,
    val isOffline: Boolean,
)

data class HomeRows(
    val hero: HomeHero?,
    val upNext: List<UpNextEntry>,
    val newEntries: List<NewEntry>,
    val notStarted: List<NotStartedEntry>,
) {
    val itemIds: List<String> by lazy {
        listOfNotNull(hero?.item?.id) +
            upNext.map { it.item.id } +
            newEntries.map { it.itemId } +
            notStarted.map { it.series.id }
    }
}

enum class HeroLabel { ContinueWatching, NextEpisode, StartSomethingNew }

data class HomeHero(
    val item: PlayableItem,
    val label: HeroLabel,
    val title: String,
    val backdropUrl: String?,
    val logoUrl: String?,
)

data class UpNextEntry(val item: PlayableItem, val isNew: Boolean)

data class NotStartedEntry(val series: Series, val seasonCount: Int)
