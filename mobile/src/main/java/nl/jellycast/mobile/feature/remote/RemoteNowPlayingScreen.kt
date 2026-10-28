package nl.jellycast.mobile.feature.remote

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.RailCircleButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.androidtv.TvKey
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.ui.info.ItemInfoPanel
import org.koin.compose.viewmodel.koinViewModel

/** Over the whole app: the now-playing screen, the "Turning on…" status and command failures. */
@Composable
internal fun RemoteNowPlayingOverlay(viewModel: RemoteNowPlayingViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Keeps drawing the last state while the screen slides away.
    var shown by remember { mutableStateOf(state) }
    if (state.isOpen) shown = state
    val navigation = LocalAppNavigation.current

    BackHandler(enabled = state.isOpen) { viewModel.onClose() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            RemoteNowPlayingScreen(
                state = shown,
                onClose = viewModel::onClose,
                onCommand = viewModel::onCommand,
                onVolumeKey = viewModel::onVolumeKey,
                onContinueHere = { session ->
                    val playable = shown.playable ?: return@RemoteNowPlayingScreen
                    viewModel.onContinueHere(session)
                    navigation.play(
                        continueHereRequest(playable, session, System.currentTimeMillis()),
                    )
                },
                onHandedOver = viewModel::onHandedOver,
            )
        }
        WakingStatus(state.waking, Modifier.align(Alignment.BottomCenter))
    }
    if (state.commandFailed) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissCommandFailure,
            title = { Text(stringResource(R.string.remote_command_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissCommandFailure) {
                    Text(stringResource(R.string.remote_ok))
                }
            },
        )
    }
}

@Composable
private fun WakingStatus(name: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(name != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            Modifier.padding(bottom = WakingBottomInset),
            shape = CircleShape,
            color = JellyCastColors.Rail,
        ) {
            Row(
                Modifier.height(44.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = JellyCastColors.Text,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.remote_turning_on, name.orEmpty()),
                    style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = JellyCastColors.Text,
                )
            }
        }
    }
}

// Above the tab bar and the TV bar.
private val WakingBottomInset = 148.dp
private val SwipeCloseDistance = 120.dp
private const val SPLIT_LEADING_FRACTION = 0.55f

/** What another device plays: the player's portrait layout with its status where the video is. */
@Composable
internal fun RemoteNowPlayingScreen(
    state: RemoteNowPlayingUiState,
    onClose: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onVolumeKey: (TvKey) -> Unit,
    onContinueHere: (RemoteSession) -> Unit,
    onHandedOver: (RemoteSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session ?: return
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showsPlayOn by remember { mutableStateOf(false) }
    val closeDistance = with(LocalDensity.current) { SwipeCloseDistance.toPx() }
    val focus = remember { FocusRequester() }
    val volumeConnected = state.volume.isConnected
    LaunchedEffect(volumeConnected) { if (volumeConnected) focus.requestFocus() }

    val swipeToClose =
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (dragOffset > closeDistance) onClose()
                    dragOffset = 0f
                },
                onDragCancel = { dragOffset = 0f },
            ) { _, amount -> dragOffset = (dragOffset + amount).coerceAtLeast(0f) }
        }
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        RemoteStatusHeader(
            state = state,
            session = session,
            onClose = onClose,
            onCommand = onCommand,
            onVolumeKey = onVolumeKey,
            modifier = headerModifier.then(swipeToClose),
        )
    }
    val isSplit =
        LocalLayout.current.isRegular &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            state.infoSubject != null

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset }
            .background(JellyCastColors.Background)
            .statusBarsPadding()
            .onPreviewKeyEvent { event -> volumeConnected && handleVolumeKey(event, onVolumeKey) }
            .focusRequester(focus)
            .focusable(),
    ) {
        if (isSplit) {
            Row(Modifier.fillMaxSize()) {
                header(Modifier.weight(SPLIT_LEADING_FRACTION))
                InfoArea(state, session, Modifier.weight(1 - SPLIT_LEADING_FRACTION)) {
                    showsPlayOn = true
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.fillMaxWidth())
                InfoArea(state, session, Modifier.weight(1f)) { showsPlayOn = true }
            }
        }
    }

    val playable = state.playable
    if (showsPlayOn && playable != null) {
        PlayOnSheetHost(
            target =
                PlayOnTarget(
                    item = playable,
                    handOffPositionMs = session.positionAt(System.currentTimeMillis()),
                    excludedDeviceId = session.deviceId,
                ),
            onDismiss = { showsPlayOn = false },
            onPlayHere = { onContinueHere(session) },
            onHandOff = { onHandedOver(session) },
        )
    }
}

/** Volume keys press the TV's while its remote connection is open; everything else passes. */
private fun handleVolumeKey(
    event: ComposeKeyEvent,
    onVolumeKey: (TvKey) -> Unit,
): Boolean {
    val key =
        when (event.key.nativeKeyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKey.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE -> TvKey.Mute
            else -> return false
        }
    if (event.type == KeyEventType.KeyDown) onVolumeKey(key)
    return true
}

@Composable
private fun InfoArea(
    state: RemoteNowPlayingUiState,
    session: RemoteSession,
    modifier: Modifier = Modifier,
    onContinueOn: () -> Unit,
) {
    val subject = state.infoSubject
    if (subject == null) {
        Text(
            remoteTitle(session.item),
            style = JellyCastText.Title2.copy(fontWeight = FontWeight.ExtraBold),
            color = JellyCastColors.Text,
            modifier = modifier.fillMaxWidth().padding(LocalLayout.current.margin),
        )
        return
    }
    ItemInfoPanel(
        subject = subject,
        modifier = modifier,
        playingItemId = session.item.id,
        actions = {
            if (state.isControllable) {
                RailCircleButton(
                    R.drawable.ic_tv,
                    stringResource(R.string.remote_continue_on),
                    onContinueOn,
                    size = 34.dp,
                )
            }
        },
    )
}

private val Scrim =
    Brush.verticalGradient(
        0f to JellyCastColors.Background.copy(alpha = 0.7f),
        0.55f to JellyCastColors.Background.copy(alpha = 0.78f),
        1f to JellyCastColors.Background.copy(alpha = 0.96f),
    )

/** In place of the video: the still dimmed, which device plays it, and how far it is. */
@Composable
private fun RemoteStatusHeader(
    state: RemoteNowPlayingUiState,
    session: RemoteSession,
    onClose: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onVolumeKey: (TvKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.aspectRatio(16f / 9f).background(JellyCastColors.Background)) {
        RemoteImage(session.item.imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Scrim))
        Column(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isControllable) {
                ControllableHeader(state, session, onClose, onCommand, onVolumeKey)
            } else {
                WatchOnlyHeader(state, session, onClose)
            }
        }
    }
}

@Composable
private fun ColumnScope.ControllableHeader(
    state: RemoteNowPlayingUiState,
    session: RemoteSession,
    onClose: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onVolumeKey: (TvKey) -> Unit,
) {
    val nowMs by rememberNowMs()
    var showsTracks by remember { mutableStateOf(false) }
    // Hides the pill right away: the TV's new position only arrives with its next report.
    var skippedStartMs by remember(session.item.id) { mutableStateOf<Long?>(null) }
    val tracks = state.tracks
    RemoteTopBar(
        session = session,
        hasTracks = tracks != null,
        volume = state.volume,
        onClose = onClose,
        onTracks = { showsTracks = true },
        onCommand = onCommand,
    )
    Spacer(Modifier.weight(1f))
    RemoteTransport(
        session = session,
        nowMs = { nowMs },
        showsVolume = state.volume.isConnected,
        onCommand = onCommand,
        onVolumeKey = onVolumeKey,
    )
    Spacer(Modifier.weight(1f))
    RemoteScrubber(
        session = session,
        nowMs = nowMs,
        skippable = skippableSegment(state.segments, session.positionAt(nowMs), skippedStartMs),
        onSkip = { segment ->
            skippedStartMs = segment.startMs
            onCommand(RemoteCommand.Seek(segment.endMs))
        },
        onSeek = { onCommand(RemoteCommand.Seek(it)) },
    )
    if (showsTracks && tracks != null) {
        RemoteTrackSheet(session, tracks, onCommand, onDismiss = { showsTracks = false })
    }
}

@Composable
private fun ColumnScope.WatchOnlyHeader(
    state: RemoteNowPlayingUiState,
    session: RemoteSession,
    onClose: () -> Unit,
) {
    val nowMs by rememberNowMs()
    Row(Modifier.fillMaxWidth()) {
        RemoteGlassButton(
            R.drawable.ic_remote_expand_more,
            stringResource(R.string.remote_close),
            onClose,
        )
    }
    Spacer(Modifier.weight(1f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_tv), null, Modifier.size(30.dp), JellyCastColors.Accent)
        Text(
            if (state.isStopped) {
                stringResource(R.string.remote_stopped_on, session.deviceName)
            } else {
                placeLine(session)
            },
            style = JellyCastText.Title3.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        val method = playMethodLabel(session.playMethod)
        if (method != null && !state.isStopped) {
            Text(method, style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
        }
    }
    Spacer(Modifier.weight(1f))
    RemoteTimeline(session, nowMs)
}
