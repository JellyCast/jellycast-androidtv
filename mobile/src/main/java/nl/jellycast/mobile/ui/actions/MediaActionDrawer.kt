package nl.jellycast.mobile.ui.actions

import androidx.compose.runtime.Composable
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series

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
 * Shows nothing while [target] is null.
 *
 * Owner: the library worker. STUB.
 */
@Composable
fun MediaActionDrawer(
    target: ActionTarget?,
    onDismiss: () -> Unit,
    allowsNavigation: Boolean = true,
) = Unit
