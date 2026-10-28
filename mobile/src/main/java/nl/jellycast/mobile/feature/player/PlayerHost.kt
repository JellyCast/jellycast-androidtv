package nl.jellycast.mobile.feature.player

import android.graphics.Rect as AndroidRect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.settings.AudioOutputDelay
import nl.jellycast.mobile.navigation.LocalAppNavigation
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Takes `AppNavigation.playRequest`, owns the one play of the app and shows it full screen or
 * as the mini player at the bottom end, [bottomInset] above the bottom edge (tab bar and any
 * accessory bar included). Drawn by the shell over everything.
 */
@Composable
fun PlayerHost(bottomInset: Dp) {
    // Composed outside the tab back stacks, so this is the Activity's ViewModel.
    val viewModel: NowPlayingViewModel = koinViewModel()
    val playerFactory: PlayerFactory = koinInject()
    val audioDelay: AudioOutputDelay = koinInject()
    val navigation = LocalAppNavigation.current
    val request = navigation.playRequest
    LaunchedEffect(request) {
        if (request != null) {
            navigation.playRequest = null
            viewModel.onPlayRequest(request)
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val engine = rememberPlayerEngine(state.load, viewModel, playerFactory, audioDelay)
    if (state.item != null) NowPlaying(state, engine, viewModel, bottomInset)
}

/** Whether the app's own player shows, full screen or as the mini player. */
@Composable
fun isPlayerOpen(): Boolean {
    val viewModel: NowPlayingViewModel = koinViewModel()
    return viewModel.uiState.collectAsStateWithLifecycle().value.item != null
}

/** Sizes of the player in this window, in px. */
private data class HostGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val statusTopPx: Float,
    val miniWidthPx: Float,
    val miniEndPx: Float,
    val miniBottomPx: Float,
)

@Composable
private fun NowPlaying(
    state: NowPlayingUiState,
    engine: PlayerEngineState,
    actions: PlayerActions,
    bottomInset: Dp,
) {
    val item = state.item ?: return
    val chrome = rememberPlayerChromeState()
    val regular = LocalLayout.current.isRegular
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geometry = hostGeometry(regular, state.isMiniPlayerLarge, bottomInset)
        val landscape = maxWidth > maxHeight
        val pip =
            rememberPictureInPicture(
                autoEnter = state.load != null && engine.playWhenReady,
                sourceRect = pinnedSourceRect(geometry, landscape, regular, state.isExpanded),
            )
        val expanded = state.isExpanded && !pip.isActive
        val fullScreen = expanded && (if (regular) chrome.videoOnly else landscape)
        val headerMode = expanded && !fullScreen && regular && landscape
        val videoRect: () -> Rect = {
            val swipe = SwipeTransform(chrome.swipeOffset.value, geometry.heightPx)
            val whole = Rect(0f, 0f, geometry.widthPx, geometry.heightPx)
            when {
                pip.isActive -> whole

                !state.isExpanded ->
                    miniPlayerRect(
                        geometry.widthPx,
                        geometry.heightPx,
                        geometry.miniWidthPx,
                        geometry.miniEndPx,
                        geometry.miniBottomPx,
                        chrome.pinchScale * chrome.miniAppear.value,
                    )

                fullScreen -> swipe.apply(whole, geometry.widthPx)

                headerMode -> chrome.headerRect

                else -> swipe.apply(
                    pinnedVideoRect(geometry.widthPx, geometry.statusTopPx),
                    geometry.widthPx,
                )
            }
        }

        if (expanded) {
            FullPlayerChrome(
                item = item,
                chrome = chrome,
                fullScreen = fullScreen,
                headerMode = headerMode,
                heightPx = geometry.heightPx,
            )
        }
        VideoSurface(
            player = engine.player,
            showsSubtitles = state.isExpanded || pip.isActive,
            modifier = Modifier.placeAt(videoRect),
        )
        when {
            pip.isActive -> Unit

            expanded ->
                FullPlayer(
                    state = state,
                    engine = engine,
                    chrome = chrome,
                    actions = actions,
                    fullScreen = fullScreen,
                    regular = regular,
                    pip = pip,
                    heightPx = geometry.heightPx,
                    videoRect = videoRect,
                )

            else ->
                MiniPlayerOverlay(
                    large = state.isMiniPlayerLarge,
                    engine = engine,
                    phase = state.phase,
                    onExpand = actions::onExpand,
                    onClose = actions::onClose,
                    onPinch = { chrome.pinchScale = it.coerceIn(MIN_PINCH, MAX_PINCH) },
                    onPinchEnd = {
                        chrome.pinchScale = 1f
                        actions.onMiniPlayerSizeChange(pinchResult(state.isMiniPlayerLarge, it))
                    },
                    modifier = Modifier.placeAt(videoRect),
                )
        }
        SystemBarsHidden(hidden = fullScreen)
    }
    MiniPlayerAppear(state.isExpanded, chrome)
}

private const val MIN_PINCH = 0.8f
private const val MAX_PINCH = 1.25f
private const val MINI_APPEAR_FROM = 0.6f
private const val MINI_APPEAR_MS = 200

@Composable
private fun MiniPlayerAppear(expanded: Boolean, chrome: PlayerChromeState) {
    LaunchedEffect(expanded) {
        if (!expanded) {
            chrome.miniAppear.snapTo(MINI_APPEAR_FROM)
            chrome.miniAppear.animateTo(1f, tween(MINI_APPEAR_MS))
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.hostGeometry(
    regular: Boolean,
    large: Boolean,
    bottomInset: Dp,
): HostGeometry {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val shortSide = minOf(maxWidth, maxHeight)
    val miniWidth =
        when {
            regular -> if (large) 480.dp else 320.dp
            large -> shortSide - 32.dp
            else -> 200.dp
        }
    return with(density) {
        HostGeometry(
            widthPx = constraints.maxWidth.toFloat(),
            heightPx = constraints.maxHeight.toFloat(),
            statusTopPx = statusTop,
            miniWidthPx = miniWidth.toPx(),
            miniEndPx = 16.dp.toPx(),
            // The shell's inset already holds the tab bar; on tablets it adds its own 16 dp.
            miniBottomPx =
                bottomInset.toPx() + if (regular) navBottom else 16.dp.toPx(),
        )
    }
}

/** Where the video is when the app leaves, so the system PiP animation starts there. */
private fun pinnedSourceRect(
    geometry: HostGeometry,
    landscape: Boolean,
    regular: Boolean,
    expanded: Boolean,
): AndroidRect? {
    if (!expanded) return null
    val rect =
        if (landscape && !regular) {
            Rect(0f, 0f, geometry.widthPx, geometry.heightPx)
        } else {
            pinnedVideoRect(geometry.widthPx, geometry.statusTopPx)
        }
    return AndroidRect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
}

@Composable
internal fun safeDrawingPadding() = WindowInsets.safeDrawing.asPaddingValues()
