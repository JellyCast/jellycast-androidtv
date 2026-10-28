package nl.jellycast.mobile.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerSheetKind { Tracks, Quality, ContinueOn }

private const val CONTROLS_HIDE_MS = 3_000L
private const val CLOSE_MS = 200
private const val SPRING_BACK_MS = 250

/** The player's own UI state: controls, sheets, scrubbing, the swipe and the tap-seek run. */
@Stable
class PlayerChromeState(private val scope: CoroutineScope) {
    var controlsVisible by mutableStateOf(true)
        private set
    var rearm by mutableIntStateOf(0)
        private set
    var sheet by mutableStateOf<PlayerSheetKind?>(null)
    var scrubFraction by mutableStateOf<Float?>(null)
    var videoOnly by mutableStateOf(false)
    var seekRun by mutableStateOf<SeekRun?>(null)
        private set
    var lastSeekSide by mutableStateOf(TapSide.Forward)
        private set
    var slowHint by mutableStateOf<SegmentPill.SlowConnection?>(null)

    /** Where the panel's header placeholder is (tablet landscape); read in layout only. */
    var headerRect by mutableStateOf(Rect.Zero)

    var pinchScale by mutableFloatStateOf(1f)

    val swipeOffset = Animatable(0f)
    val miniAppear = Animatable(1f)

    private val tracker = TapSeekTracker()
    private var settleJob: Job? = null

    fun showControls() {
        controlsVisible = true
        rearm++
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun toggleControls() {
        controlsVisible = !controlsVisible
        if (controlsVisible) rearm++
    }

    /** Any touch on the controls keeps them up for another 3 s. */
    fun interact() {
        rearm++
    }

    fun openSheet(kind: PlayerSheetKind) {
        sheet = kind
        controlsVisible = true
    }

    fun closeSheet() {
        sheet = null
        rearm++
    }

    fun dragTo(offsetPx: Float) {
        scope.launch { swipeOffset.snapTo(offsetPx) }
    }

    /** Slides the player down and out, then hands over to the mini player. */
    fun animateClose(heightPx: Float, onClosed: () -> Unit) {
        scope.launch {
            swipeOffset.animateTo(heightPx, tween(CLOSE_MS, easing = FastOutLinearInEasing))
            onClosed()
            swipeOffset.snapTo(0f)
        }
    }

    fun springBack() {
        scope.launch { swipeOffset.animateTo(0f, tween(SPRING_BACK_MS)) }
    }

    fun onVideoTap(side: TapSide, nowMs: Long, positionMs: Long, onSeek: (SeekRun) -> Unit) {
        val outcome = tracker.tap(side, nowMs, positionMs)
        if (outcome.toggleControls) toggleControls()
        val run = outcome.run ?: return
        seekRun = run
        lastSeekSide = run.side
        settleJob?.cancel()
        settleJob =
            scope.launch {
                delay(SEEK_SETTLE_MS)
                tracker.settle()?.let(onSeek)
                seekRun = null
            }
    }
}

@Composable
fun rememberPlayerChromeState(): PlayerChromeState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerChromeState(scope) }
}

/** Hides the controls after 3 s, only while playing, not scrubbing and with no sheet open. */
@Composable
fun ControlsAutoHide(chrome: PlayerChromeState, isPlaying: Boolean) {
    val scrubbing = chrome.scrubFraction != null
    LaunchedEffect(chrome.controlsVisible, chrome.rearm, isPlaying, scrubbing, chrome.sheet) {
        if (chrome.controlsVisible && isPlaying && !scrubbing && chrome.sheet == null) {
            delay(CONTROLS_HIDE_MS)
            chrome.hideControls()
        }
    }
}
