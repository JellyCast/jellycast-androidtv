package nl.jellycast.tv.feature.player

import android.app.Activity
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.playback.DisplayModeChoice
import nl.jellycast.tv.core.playback.DisplayModeSpec
import nl.jellycast.tv.core.playback.FrameRateMode
import nl.jellycast.tv.core.playback.chooseDisplayMode
import nl.jellycast.tv.core.playback.displayModeReached
import nl.jellycast.tv.core.playback.displaySettled
import timber.log.Timber

/** Lets the system restore its own display mode. */
private const val NO_PREFERRED_MODE = 0

/**
 * [modeReady]: the video decoder may start. [settled]: audio may start. [switchedAtMs]: when the
 * mode switched non-seamlessly.
 */
internal data class FrameRateState(
    val modeReady: Boolean,
    val settled: Boolean,
    val switchedAtMs: Long?,
)

/** Errors this soon after a mode switch are often caused by the HDMI resync. */
internal const val AFTER_SWITCH_WINDOW_MS = 10_000L

/**
 * Switches the display to the mode matching the video frame rate and restores it on leaving. The
 * Chromecast only switches seamlessly on its own, which it cannot do, so the app picks the mode.
 *
 * Audio may start after [startDelayMs] when nothing switches or the switch is seamless; otherwise
 * only once the new mode is active, HDMI (picture and HDCP) is stable and the audio output is back.
 */
@Composable
internal fun rememberFrameRateMatching(
    videoFrameRate: Float?,
    videoWidth: Int?,
    videoHeight: Int?,
    frameRateMode: FrameRateMode,
    startDelayMs: Long,
    log: (String) -> Unit,
): FrameRateState {
    val activity =
        LocalActivity.current
            ?: return FrameRateState(modeReady = true, settled = true, switchedAtMs = null)
    val choice =
        remember(activity, videoFrameRate, videoWidth, videoHeight, frameRateMode) {
            activity.chooseMode(videoFrameRate, videoWidth, videoHeight, frameRateMode)
        }
    val mode = choice?.mode
    val currentLog by rememberUpdatedState(log)
    val needsResync = mode != null && !choice.seamless
    var modeReady by remember(choice) { mutableStateOf(!needsResync) }
    var settled by remember(choice) { mutableStateOf(!needsResync && startDelayMs == 0L) }
    val switchedAtMs = remember(choice) { if (needsResync) SystemClock.elapsedRealtime() else null }

    DisposableEffect(activity, mode) {
        val window = activity.window
        if (mode != null) {
            Timber.i(
                "Display mode %sx%s @ %s Hz for %s fps (%s)",
                mode.width,
                mode.height,
                mode.refreshRate,
                videoFrameRate,
                if (choice.seamless) "seamless" else "with resync",
            )
            currentLog(
                "Display mode requested: ${mode.width}x${mode.height} @ ${mode.refreshRate} Hz " +
                    if (choice.seamless) "(seamless)" else "(with resync)",
            )
            window.preferDisplayMode(mode.id)
        } else {
            currentLog("No mode switch")
        }
        onDispose {
            if (mode != null) window.preferDisplayMode(NO_PREFERRED_MODE)
        }
    }

    LaunchedEffect(activity, choice, startDelayMs) {
        val display = activity.display
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val audioManager = activity.getSystemService(AudioManager::class.java)
        if (display == null || displayManager == null || audioManager == null) {
            modeReady = true
            settled = true
            return@LaunchedEffect
        }
        val requestedAt = System.currentTimeMillis()
        if (needsResync) {
            launch {
                displayModeReached(mode?.id, displayManager.activeModeIds(display)).first { it }
                val activeId = display.mode.modeId
                Timber.i("Display mode %s active, decoder may start", activeId)
                currentLog(
                    if (activeId == mode?.id) {
                        "Display mode active"
                    } else {
                        "Display mode did not arrive in time (active: mode $activeId)"
                    },
                )
                modeReady = true
            }
        }
        displaySettled(
            // Seamless: no HDMI resync, so only the start delay.
            targetModeId = mode?.id.takeIf { needsResync },
            activeModeIds = displayManager.activeModeIds(display),
            audioRoute = audioManager.hdmiAudioPresent(),
            extraDelayMs = startDelayMs,
        ).collect {
            if (it && needsResync) {
                val afterMs = System.currentTimeMillis() - requestedAt
                Timber.i("Display mode stable after %s ms", afterMs)
                currentLog("HDMI stable, audio may start")
            }
            settled = it
        }
    }
    return FrameRateState(modeReady, settled, switchedAtMs)
}

/**
 * Whether an HDMI audio output is present, on every change (not the initial state). Without HDMI
 * at the start (TV speakers, Bluetooth) nothing is emitted: there is no resync to wait for.
 */
private fun AudioManager.hdmiAudioPresent(): Flow<Boolean> = callbackFlow {
    fun present() = getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_HDMI ||
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
            it.type == AudioDeviceInfo.TYPE_HDMI_EARC
    }
    val initial = present()
    var last = initial
    val callback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = changed()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
                changed()

            private fun changed() {
                val now = present()
                if (initial && now != last) {
                    Timber.d("HDMI audio %s", if (now) "back" else "gone")
                    trySend(now)
                }
                last = now
            }
        }
    registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    awaitClose { unregisterAudioDeviceCallback(callback) }
}

/** The mode to switch to, or `null` when there is nothing to do or the user turned it off. */
private fun Activity.chooseMode(
    videoFrameRate: Float?,
    videoWidth: Int?,
    videoHeight: Int?,
    frameRateMode: FrameRateMode,
): DisplayModeChoice? {
    if (frameRateMode == FrameRateMode.Off) return null
    val display = display ?: return null
    val userPreference =
        getSystemService(DisplayManager::class.java)?.matchContentFrameRateUserPreference
    if (userPreference == DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER) return null
    val seamlessOnly =
        frameRateMode == FrameRateMode.System &&
            userPreference == DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY
    return chooseDisplayMode(
        modes = display.supportedModes.map { it.toSpec() },
        current = display.mode.toSpec(),
        videoFrameRate = videoFrameRate,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        seamlessOnly = seamlessOnly,
    )
}

private fun DisplayManager.activeModeIds(display: Display): Flow<Int> = callbackFlow {
    val listener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == display.displayId) trySend(display.mode.modeId)
            }

            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit
        }
    registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    trySend(display.mode.modeId)
    awaitClose { unregisterDisplayListener(listener) }
}

private fun Display.Mode.toSpec() = DisplayModeSpec(
    id = modeId,
    width = physicalWidth,
    height = physicalHeight,
    refreshRate = refreshRate,
    alternativeRefreshRates = alternativeRefreshRates.toList(),
)

private fun Window.preferDisplayMode(modeId: Int) {
    attributes = attributes.apply { preferredDisplayModeId = modeId }
}
