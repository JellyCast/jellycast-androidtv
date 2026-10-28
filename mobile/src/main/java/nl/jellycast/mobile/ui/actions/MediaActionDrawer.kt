package nl.jellycast.mobile.ui.actions

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.jellycast.core.common.Clock
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.SheetRow
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.navigation.SeriesRoute
import nl.jellycast.mobile.ui.downloads.DownloadButton
import nl.jellycast.mobile.ui.remote.PlayOnRequest
import nl.jellycast.mobile.ui.remote.PlayOnSheet
import nl.jellycast.mobile.ui.remote.hasRemoteTargets
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/** What a long press opened the action drawer for. */
sealed interface ActionTarget {
    data class MovieTarget(val movie: Movie) : ActionTarget

    data class SeriesTarget(val series: Series) : ActionTarget

    data class EpisodeTarget(val episode: Episode) : ActionTarget

    data class SeasonTarget(val series: Series, val season: Int, val episodes: List<Episode>) :
        ActionTarget
}

/**
 * The long-press bottom drawer (iOS `ActionDrawer` + `MediaContextMenu`): a header with the
 * image, then actions such as Play, Mark as watched, Add to list…, Download, Go to series.
 * [allowsNavigation] false hides actions that leave the page (e.g. inside the player).
 * Shows nothing while [target] is null. A chosen action runs once the drawer has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionDrawer(
    target: ActionTarget?,
    onDismiss: () -> Unit,
    allowsNavigation: Boolean = true,
) {
    var playOn by remember { mutableStateOf<PlayOnRequest?>(null) }
    PlayOnSheet(playOn, onDismiss = { playOn = null })
    if (target == null) return

    val store: LibraryStore = koinInject()
    val connectivity: Connectivity = koinInject()
    val preferences: AppPreferences = koinInject()
    val clock: Clock = koinInject()
    val appScope: CoroutineScope = koinInject(named(APP_SCOPE))
    val navigation = LocalAppNavigation.current
    val uriHandler = LocalUriHandler.current

    val snapshot by store.snapshot.collectAsStateWithLifecycle()
    val isOffline by connectivity.isOffline.collectAsStateWithLifecycle()
    val ratingSource by preferences.ratingSource.collectAsStateWithLifecycle()
    val hasRemote = hasRemoteTargets()
    val actions =
        remember(target, snapshot, isOffline, ratingSource, hasRemote, allowsNavigation) {
            drawerActions(
                target,
                DrawerContext(
                    index = snapshot?.let { LibraryIndex(it, clock.nowMs()) },
                    isOffline = isOffline,
                    hasRemoteTargets = hasRemote,
                    ratingSource = ratingSource,
                    allowsNavigation = allowsNavigation,
                ),
            )
        }
    val listTitle = listTitle(target)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun choose(action: DrawerAction) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            when (action) {
                is DrawerAction.Play -> navigation.play(PlayRequest(action.item))

                is DrawerAction.PlayFromStart ->
                    navigation.play(PlayRequest(action.item, fromStart = true))

                is DrawerAction.PlayOn -> playOn = PlayOnRequest(action.item)

                is DrawerAction.SetPlayed ->
                    appScope.launch { store.setPlayed(action.itemIds, action.played) }

                is DrawerAction.AddToList -> navigation.addToList(listTitle, action.itemIds)

                is DrawerAction.GoToMovie -> navigation.open(MovieRoute(action.movieId))

                is DrawerAction.GoToSeries -> navigation.open(SeriesRoute(action.seriesId))

                is DrawerAction.OpenOn -> uriHandler.openUri(action.url)

                is DrawerAction.Download -> Unit
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JellyCastColors.Surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrawerHeader(target, Modifier.padding(bottom = 6.dp))
            actions.forEach { action -> DrawerRow(action, onClick = { choose(action) }) }
        }
    }
}

@Composable
private fun DrawerRow(action: DrawerAction, onClick: () -> Unit) {
    if (action is DrawerAction.Download) {
        DownloadButton(action.itemId, Modifier.fillMaxWidth())
        return
    }
    val accented = action is DrawerAction.Play
    SheetRow(
        title = actionTitle(action),
        onClick = onClick,
        icon = actionIcon(action),
        iconTint = if (accented) JellyCastColors.Accent else JellyCastColors.Text,
    )
}

@Composable
private fun actionTitle(action: DrawerAction): String = when (action) {
    is DrawerAction.Play ->
        when {
            action.isInProgress -> stringResource(R.string.library_drawer_continue)

            action.episodeLabel != null ->
                stringResource(
                    R.string.library_drawer_play_episode,
                    episodeCode(action.episodeLabel),
                )

            else -> stringResource(R.string.library_drawer_play)
        }

    is DrawerAction.PlayFromStart -> stringResource(R.string.library_drawer_play_from_start)

    is DrawerAction.PlayOn -> stringResource(R.string.library_drawer_play_on)

    is DrawerAction.SetPlayed ->
        if (action.played) {
            stringResource(R.string.library_drawer_mark_watched)
        } else {
            stringResource(R.string.library_drawer_mark_not_watched)
        }

    is DrawerAction.AddToList -> stringResource(R.string.library_drawer_add_to_list)

    is DrawerAction.GoToMovie -> stringResource(R.string.library_drawer_go_to_movie)

    is DrawerAction.GoToSeries -> stringResource(R.string.library_drawer_go_to_series)

    is DrawerAction.OpenOn -> stringResource(
        R.string.library_drawer_open_on,
        action.source.siteName,
    )

    is DrawerAction.Download -> ""
}

@DrawableRes
private fun actionIcon(action: DrawerAction): Int = when (action) {
    is DrawerAction.Play -> R.drawable.ic_play

    is DrawerAction.PlayFromStart -> R.drawable.ic_library_replay

    is DrawerAction.PlayOn -> R.drawable.ic_tv

    is DrawerAction.SetPlayed ->
        if (action.played) R.drawable.ic_library_check_circle else R.drawable.ic_library_circle

    is DrawerAction.AddToList -> R.drawable.ic_library_playlist_add

    is DrawerAction.GoToMovie -> R.drawable.ic_movie

    is DrawerAction.GoToSeries -> R.drawable.ic_library

    is DrawerAction.OpenOn -> R.drawable.ic_library_open_in_new

    is DrawerAction.Download -> R.drawable.ic_download
}

/** "Series · S1 · E3" for an episode, so the list sheet says which one goes in. */
@Composable
private fun listTitle(target: ActionTarget): String = when (target) {
    is ActionTarget.MovieTarget -> target.movie.title
    is ActionTarget.SeriesTarget -> target.series.title
    is ActionTarget.EpisodeTarget -> joined(target.episode.seriesTitle, episodeCode(target.episode))
    is ActionTarget.SeasonTarget -> joined(target.series.title, seasonTitle(target.season))
}

@Composable
private fun seasonTitle(season: Int): String = if (season == 0) {
    stringResource(R.string.library_specials)
} else {
    stringResource(R.string.library_season, season)
}

private val PosterSize = DpSize(44.dp, 66.dp)
private val WideSize = DpSize(96.dp, 54.dp)

@Composable
private fun DrawerHeader(target: ActionTarget, modifier: Modifier = Modifier) {
    val movieLabel = stringResource(R.string.library_type_movie)
    val seriesLabel = stringResource(R.string.library_type_series)
    val header =
        when (target) {
            is ActionTarget.MovieTarget ->
                HeaderInfo(
                    target.movie.title,
                    joined(movieLabel, target.movie.year?.toString()),
                    target.movie.artwork.poster,
                )

            is ActionTarget.SeriesTarget ->
                HeaderInfo(
                    target.series.title,
                    joined(seriesLabel, target.series.year?.toString()),
                    target.series.artwork.poster,
                )

            is ActionTarget.EpisodeTarget ->
                HeaderInfo(
                    target.episode.seriesTitle,
                    joined(episodeCode(target.episode), target.episode.title),
                    target.episode.stillUrl ?: target.episode.artwork.backdrop,
                    isWide = true,
                )

            is ActionTarget.SeasonTarget ->
                HeaderInfo(
                    target.series.title,
                    seasonTitle(target.season),
                    target.series.artwork.poster,
                )
        }
    val size = if (header.isWide) WideSize else PosterSize
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            header.imageUrl,
            header.title,
            Modifier.size(size.width, size.height),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                header.title,
                style = JellyCastText.Headline,
                color = JellyCastColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.subtitle.isNotEmpty()) {
                Text(
                    header.subtitle,
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class HeaderInfo(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val isWide: Boolean = false,
)

private fun joined(vararg parts: String?): String =
    parts.filterNot { it.isNullOrEmpty() }.joinToString(" · ")
