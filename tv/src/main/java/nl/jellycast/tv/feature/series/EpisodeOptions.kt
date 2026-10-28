package nl.jellycast.tv.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.model.Episode
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.OptionRow
import nl.jellycast.tv.core.designsystem.component.OptionsDivider
import nl.jellycast.tv.core.designsystem.component.OptionsPanel
import nl.jellycast.tv.core.ui.episodeCode

data class EpisodeActions(
    val onWatchedChange: (Episode, watched: Boolean) -> Unit = { _, _ -> },
    val onOpenSeries: (Episode) -> Unit = {},
)

@Composable
internal fun EpisodeOptions(
    episode: Episode,
    onWatchedChange: (watched: Boolean) -> Unit,
    onOpenSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = episodeCode(episode.seasonNumber, episode.episodeNumber)
    OptionsPanel(title = "${episode.seriesTitle} · $code", modifier = modifier) { first ->
        OptionRow(
            text = stringResource(R.string.detail_mark_watched),
            onClick = { onWatchedChange(true) },
            modifier = Modifier.focusRequester(first),
        )
        // An unstarted next episode is already unwatched.
        if (episode.resumePositionMs > 0) {
            OptionRow(
                text = stringResource(R.string.movies_mark_unwatched),
                onClick = { onWatchedChange(false) },
            )
        }
        OptionsDivider()
        OptionRow(text = stringResource(R.string.series_open_page), onClick = onOpenSeries)
    }
}
