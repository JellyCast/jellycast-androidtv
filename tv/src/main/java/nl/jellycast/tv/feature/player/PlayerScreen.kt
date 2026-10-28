package nl.jellycast.tv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.playback.PlaybackDiagnostics
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.PlaybackStartLog
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.tv.BuildConfig
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.TvScreen
import nl.jellycast.tv.core.ui.episodeCode
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val OVERLAY_FADE_MS = 150

@Composable
fun PlayerScreen(
    itemId: String,
    startPositionMs: Long,
    requestedStreams: StreamChoice,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel =
        koinViewModel { parametersOf(itemId, startPositionMs, requestedStreams) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        // Black, so no other screen flashes between the click and the first frame.
        PlayerUiState.Loading -> Box(modifier.fillMaxSize().background(Color.Black))

        is PlayerUiState.Error ->
            TvScreen(
                title = stringResource(R.string.player_error_title),
                subtitle = state.detail ?: stringResource(R.string.player_error_unreachable),
                modifier = modifier,
            ) {
                Button(onClick = viewModel::onRetry) { Text(stringResource(R.string.action_retry)) }
            }

        is PlayerUiState.Ready ->
            PlayerContent(
                plan = state.plan,
                info = state.info,
                onStop = viewModel::onPlayerStopped,
                onError = viewModel::onPlaybackError,
                onStable = viewModel::onPlaybackStable,
                onPlayNext = viewModel::onPlayNext,
                onPlayItem = viewModel::onPlayItem,
                onNearEnd = viewModel::onNearEnd,
                onExit = onExit,
                modifier = modifier,
            )
    }
}

@Composable
private fun PlayerContent(
    plan: PlaybackPlan,
    info: PlayerInfo,
    onStop: (positionMs: Long) -> Unit,
    onError: PlaybackErrorHandler,
    onStable: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayItem: (itemId: String, startPositionMs: Long, streams: StreamChoice) -> Unit,
    onNearEnd: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    playerFactory: PlayerFactory = koinInject(),
    reporter: PlaybackReporter = koinInject(),
    settingsStore: PlaybackSettingsStore = koinInject(),
    startLog: PlaybackStartLog = koinInject(),
) {
    val currentOnExit by rememberUpdatedState(onExit)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val nextEpisode = info.nextEpisode
    val jump = remember(plan) { JumpState() }
    val diagnostics = remember(plan) { PlaybackDiagnostics() }
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val session =
        rememberPlayerSession(
            plan = plan,
            info = info,
            settings = settings,
            diagnostics = diagnostics,
            nextAvailable = nextEpisode != null && !jump.nextDismissed,
            onStop = onStop,
            onError = onError,
            onStable = onStable,
            onPlayNext = onPlayNext,
            onExit = onExit,
            playerFactory = playerFactory,
            reporter = reporter,
            settingsStore = settingsStore,
            startLog = startLog,
        )
    val player = session.player
    RemoteControlEffect(
        plan,
        session,
        reporter,
        onStop = currentOnExit,
        onPlayNext = currentOnPlayNext,
        onPlayItem = onPlayItem,
    )

    val segments = info.segments
    JumpEffects(jump, player, nextEpisode, segments, onNearEnd)
    // The next episode wins over a segment to skip.
    val showNextPill = nextEpisode != null && jump.nearEnd && !jump.nextDismissed
    val skip = jump.skipSegment?.takeIf { !showNextPill }
    val jumpVisible = showNextPill || skip != null

    val overlay = remember { OverlayState() }
    NextEpisodeCountdown(jump, showNextPill, overlay.visible, onFinish = currentOnPlayNext)
    OverlayEffects(overlay, session.isPlaying, jumpVisible)
    PlayerBackHandler(overlay, jump, showNextPill, skip, onExit = currentOnExit)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { overlay.onPlayerKey(it, session.player, jumpVisible) },
    ) {
        PlayerSurface(
            player = player,
            subtitleSize = settings.subtitleSize,
            subtitleBackground = settings.subtitleBackground,
            idleRequester = overlay.idleRequester,
        )

        AnimatedVisibility(
            visible = overlay.visible && player != null,
            enter = fadeIn(tween(OVERLAY_FADE_MS)),
            exit = fadeOut(tween(OVERLAY_FADE_MS)),
        ) {
            if (player != null) {
                PlayerOverlay(
                    player = player,
                    title = info.title(),
                    subtitle = info.subtitle(),
                    hasNext = nextEpisode != null,
                    requesters = overlay.requesters,
                    jumpRequester = overlay.jumpRequester.takeIf { jumpVisible },
                    onOpenAudio = { overlay.panel = PlayerPanel.Audio },
                    onOpenSubtitles = { overlay.panel = PlayerPanel.Subtitles },
                    // Debug builds always show the playback info, so they have no info button.
                    onToggleInfo = if (BuildConfig.DEV_TOOLS) null else overlay::toggleInfo,
                    onPlayNext = currentOnPlayNext,
                )
            }
        }

        if (player != null && overlay.panel != PlayerPanel.None) {
            PlayerPanelMenu(
                panel = overlay.panel,
                player = player,
                tracks = session.tracks,
                settings = settings,
                settingsStore = settingsStore,
                preferredLanguages = plan.preferredLanguages,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (jumpVisible) {
            PlayerJumpPill(
                nextEpisode = nextEpisode.takeIf { showNextPill },
                skip = skip,
                overlayVisible = overlay.visible,
                focusRequester = overlay.jumpRequester,
                countdownProgress = { jump.countdownProgress },
                onPlayNext = currentOnPlayNext,
                onSkip = { segment ->
                    session.player?.seekTo(segment.endMs)
                    jump.skipped(segment, segments)
                },
                onFocus = { overlay.jumpHadFocus = true },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (BuildConfig.DEV_TOOLS || overlay.panel == PlayerPanel.Info) {
            val state by diagnostics.state.collectAsStateWithLifecycle()
            PlaybackInfoPanel(plan, state, Modifier.align(Alignment.TopStart))
        }
    }
}

/** Series name for an episode, otherwise the movie title. */
private fun PlayerInfo.title(): String = when (val item = item) {
    is PlayableItem.EpisodeItem -> item.episode.seriesTitle
    is PlayableItem.MovieItem -> item.movie.title
    null -> ""
}

@Composable
private fun PlayerInfo.subtitle(): String? {
    val episode = (item as? PlayableItem.EpisodeItem)?.episode ?: return null
    return "${episodeCode(episode.seasonNumber, episode.episodeNumber)}  ${episode.title}".trim()
}
