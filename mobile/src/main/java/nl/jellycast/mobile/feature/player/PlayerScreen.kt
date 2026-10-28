package nl.jellycast.mobile.feature.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.ui.episodeCode
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun PlayerScreen(
    itemId: String,
    startPositionMs: Long,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel =
        koinViewModel(key = "$itemId@$startPositionMs") { parametersOf(itemId, startPositionMs) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FullScreenLandscape()
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val state = uiState) {
            PlayerUiState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            is PlayerUiState.Ready ->
                PlayerSurface(
                    state = state,
                    callbacks =
                        SessionCallbacks(
                            onStop = viewModel::onPlayerStopped,
                            onError = viewModel::onPlaybackError,
                            onStable = viewModel::onPlaybackStable,
                            onPlayNext = viewModel::onPlayNext,
                            onExit = onExit,
                        ),
                    playerFactory = koinInject(),
                    reporter = koinInject(),
                )

            is PlayerUiState.Error ->
                PlayerError(state.detail, onRetry = viewModel::onRetry, onExit = onExit)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(
    state: PlayerUiState.Ready,
    callbacks: SessionCallbacks,
    playerFactory: PlayerFactory,
    reporter: PlaybackReporter,
) {
    val session =
        rememberPlayerSession(
            plan = state.plan,
            segments = state.segments,
            nextAvailable = state.nextEpisode != null,
            playerFactory = playerFactory,
            reporter = reporter,
            callbacks = callbacks,
        )
    var controllerVisible by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                }
            },
            update = { it.player = session.player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (controllerVisible) {
            TopBar(
                title = state.item?.let { playerTitle(it) } ?: state.plan.title,
                nextAvailable = state.nextEpisode != null,
                onBack = callbacks.onExit,
                onNext = callbacks.onPlayNext,
            )
        }
    }
}

@Composable
private fun TopBar(title: String, nextAvailable: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (nextAvailable) {
            OutlinedButton(onClick = onNext) { Text(stringResource(R.string.player_next_episode)) }
        }
    }
}

@Composable
private fun PlayerError(detail: String?, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(
                if (detail ==
                    null
                ) {
                    R.string.player_error_server
                } else {
                    R.string.player_error
                },
            ),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        detail?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.back)) }
    }
}

/** The plan's title is the file name; the item gives "Friends · S9 · E1 · Title". */
@Composable
private fun playerTitle(item: PlayableItem): String = when (item) {
    is PlayableItem.MovieItem -> item.movie.title

    is PlayableItem.EpisodeItem ->
        listOf(item.episode.seriesTitle, episodeCode(item.episode), item.episode.title)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
}

/** Landscape without system bars while the player is open; restored on leaving. */
@Composable
private fun FullScreenLandscape() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previous
        }
    }
}
