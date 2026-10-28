package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the info panel describes. */
sealed interface InfoSubject {
    data class MovieInfo(val movieId: String) : InfoSubject

    data class EpisodeInfo(val episodeId: String, val seriesId: String) : InfoSubject

    /** The series page: the whole series, with its seasons in the Episodes tab. */
    data class SeriesInfo(val seriesId: String) : InfoSubject
}

/**
 * The summary (title, meta line, tech badges, overview, rating link) and the tabs (Episodes,
 * Cast, Related, Chat) shown on the movie detail, the series page and under the video in the
 * player. [header] scrolls above it (the hero on detail pages; empty in the player).
 * [actions] go at the end of the link row. [playingItemId] marks the episode that plays.
 *
 * Owner: the movie/info worker. STUB.
 */
@Composable
fun ItemInfoPanel(
    subject: InfoSubject,
    modifier: Modifier = Modifier,
    playingItemId: String? = null,
    header: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    header()
}
