package nl.jellycast.mobile.ui.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The Cast tab for a page that draws its own tabs, such as the series page. */
@Composable
fun SeriesCast(seriesId: String, modifier: Modifier = Modifier) {
    val viewModel = rememberItemInfoViewModel(InfoSubject.SeriesInfo(seriesId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CastTab(
        cast = state.cast,
        source = state.ratingSource,
        isEpisode = false,
        onRetry = viewModel::onRetryCast,
        modifier = modifier,
    )
}
