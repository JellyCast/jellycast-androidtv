package nl.jellycast.tv.feature.movies

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.OptionRow
import nl.jellycast.tv.core.designsystem.component.OptionsDivider
import nl.jellycast.tv.core.designsystem.component.OptionsPanel
import nl.jellycast.tv.core.model.Movie

@Composable
internal fun MovieOptions(
    movie: Movie,
    onPlayFromStart: () -> Unit,
    onWatchedChange: (watched: Boolean) -> Unit,
    onFullOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionsPanel(title = movie.title, modifier = modifier) { first ->
        val hasProgress = movie.resumePositionMs > 0
        if (hasProgress) {
            OptionRow(
                text = stringResource(R.string.detail_from_start),
                onClick = onPlayFromStart,
                modifier = Modifier.focusRequester(first),
            )
        }
        if (!movie.isPlayed) {
            OptionRow(
                text = stringResource(R.string.detail_mark_watched),
                onClick = { onWatchedChange(true) },
                modifier = if (hasProgress) Modifier else Modifier.focusRequester(first),
            )
        }
        // Unwatched also clears the progress of a movie in Continue watching.
        if (movie.isPlayed || hasProgress) {
            OptionRow(
                text = stringResource(R.string.movies_mark_unwatched),
                onClick = { onWatchedChange(false) },
                modifier = if (movie.isPlayed) Modifier.focusRequester(first) else Modifier,
            )
        }
        OptionsDivider()
        OptionRow(text = stringResource(R.string.movies_full_overview), onClick = onFullOverview)
    }
}
