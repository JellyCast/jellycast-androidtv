package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.navigation.AppNavigation
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import nl.jellycast.mobile.ui.chat.ChatTab
import nl.jellycast.mobile.ui.downloads.OfflineAvailability
import org.koin.compose.getKoin
import org.koin.compose.koinInject

@Composable
internal fun InfoTabContent(
    panel: PanelState,
    isLarge: Boolean,
    margin: Dp,
    modifier: Modifier = Modifier,
) {
    val state = panel.state
    val navigation = LocalAppNavigation.current
    val textWidth = Modifier.widthIn(max = LocalLayout.current.textMaxWidth)
    var drawerTarget by remember { mutableStateOf<ActionTarget?>(null) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        when (panel.currentTab) {
            InfoTab.Episodes ->
                EpisodesContent(panel, isLarge, margin, navigation, onDrawer = {
                    drawerTarget = it
                })

            InfoTab.Cast ->
                CastTab(
                    cast = state.cast,
                    source = state.ratingSource,
                    isEpisode = state.subject is InfoSubject.EpisodeInfo,
                    onRetry = panel.onRetryCast,
                    modifier = textWidth,
                )

            InfoTab.Related ->
                RelatedTab(
                    collectionTitle = state.collectionTitle,
                    collection = state.collection,
                    related = state.related,
                    onSelect = { movie -> openRelated(movie, panel.inPlayer, navigation) },
                )

            InfoTab.Chat -> ChatTab(state.subject, textWidth)
        }
    }
    MediaActionDrawer(drawerTarget, onDismiss = { drawerTarget = null }, allowsNavigation = false)
}

private fun openRelated(movie: Movie, inPlayer: Boolean, navigation: AppNavigation) {
    if (inPlayer) {
        navigation.play(PlayRequest(PlayableItem.MovieItem(movie)))
    } else {
        navigation.open(MovieRoute(movie.id))
    }
}

@Composable
private fun EpisodesContent(
    panel: PanelState,
    isLarge: Boolean,
    margin: Dp,
    navigation: AppNavigation,
    onDrawer: (ActionTarget) -> Unit,
) {
    val state = panel.state
    val style =
        EpisodeRowStyle(
            series = state.series,
            ratingSource = state.ratingSource,
            isLarge = isLarge,
            playingId = panel.playingItemId,
            nextId = state.progress?.next?.id?.takeIf { state.subject is InfoSubject.SeriesInfo },
            playingOn = panel.playingOn,
            isAvailable = rememberEpisodeAvailability(),
        )
    val play: (Episode) -> Unit = { navigation.play(PlayRequest(PlayableItem.EpisodeItem(it))) }
    val longPress: (Episode) -> Unit = { onDrawer(ActionTarget.EpisodeTarget(it)) }
    val progress = state.progress
    if (state.subject is InfoSubject.SeriesInfo) {
        progress ?: return
        SeriesEpisodesTab(
            progress = progress,
            style = style,
            margin = margin,
            onPlay = play,
            onLongPress = longPress,
            onSeasonLongPress = { season ->
                val episodes = progress.episodes.filter { (it.seasonNumber ?: 0) == season }
                onDrawer(ActionTarget.SeasonTarget(progress.series, season, episodes))
            },
        )
    } else {
        EpisodeInfoList(state.episodeWindow, style, onPlay = play, onLongPress = longPress)
    }
}

@Composable
private fun rememberEpisodeAvailability(): (Episode) -> Boolean {
    val available = rememberOfflineAvailability()
    return remember(available) { { episode -> available(episode.id) } }
}

/** Whether an item can be opened: always online, only when downloaded while offline. */
@Composable
fun rememberOfflineAvailability(): (String) -> Boolean {
    val connectivity: Connectivity = koinInject()
    val isOffline by connectivity.isOffline.collectAsStateWithLifecycle()
    val koin = getKoin()
    // Bound by the downloads area; without it nothing counts as downloaded.
    val offline = remember(koin) { koin.getOrNull<OfflineAvailability>() }
    return remember(isOffline, offline) {
        { itemId -> !isOffline || offline?.isAvailableOffline(itemId) == true }
    }
}
