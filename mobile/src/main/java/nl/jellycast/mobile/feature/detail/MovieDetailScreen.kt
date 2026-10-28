package nl.jellycast.mobile.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.DetailHeader
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.feature.home.ErrorMessage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MovieDetailScreen(
    movieId: String,
    onPlay: (itemId: String, startPositionMs: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = koinViewModel(key = movieId) { parametersOf(movieId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val firstResume = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume[0]) viewModel.onShown()
        firstResume[0] = false
        onPauseOrDispose { }
    }
    MovieDetailContent(
        uiState = uiState,
        onPlay = onPlay,
        onWatchedClick = viewModel::onWatchedClick,
        onRetry = viewModel::onRetry,
        onBack = onBack,
    )
}

@Composable
fun MovieDetailContent(
    uiState: MovieDetailUiState,
    onPlay: (String, Long) -> Unit,
    onWatchedClick: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movie = uiState.movie
    when {
        movie != null -> MovieBody(movie, onPlay, onWatchedClick, onBack, modifier)

        uiState.failed -> ErrorMessage(onRetry, modifier)

        else ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
    }
}

@Composable
private fun MovieBody(
    movie: Movie,
    onPlay: (String, Long) -> Unit,
    onWatchedClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(
            backdropUrl = movie.artwork.backdrop,
            logoUrl = movie.artwork.logo,
            title = movie.title,
            onBack = onBack,
        )
        Column(
            Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                movie.metadataLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val resuming = movie.resumePositionMs > 0 && !movie.isPlayed
            Button(
                onClick = { onPlay(movie.id, if (resuming) movie.resumePositionMs else 0) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
                val label =
                    if (resuming) {
                        val left = remainingMs(movie.resumePositionMs, movie.runtimeMs)
                        if (left != null) {
                            stringResource(R.string.detail_resume_left, remainingText(left))
                        } else {
                            stringResource(R.string.detail_resume)
                        }
                    } else {
                        stringResource(R.string.detail_play)
                    }
                Text(label, Modifier.padding(start = 8.dp))
            }
            if (resuming) {
                OutlinedButton(onClick = { onPlay(movie.id, 0) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.detail_from_start))
                }
            }
            OutlinedButton(onClick = onWatchedClick, Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (movie.isPlayed) {
                            R.string.detail_watched
                        } else {
                            R.string.detail_mark_watched
                        },
                    ),
                )
            }
            movie.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Movie.metadataLine(): String = listOfNotNull(
    year?.toString(),
    runtimeMs?.let { durationText(it) },
    ageRating?.label,
    genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", "),
).joinToString(" · ")
