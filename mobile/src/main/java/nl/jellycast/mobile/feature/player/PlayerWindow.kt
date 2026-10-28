package nl.jellycast.mobile.feature.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import android.view.OrientationEventListener
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import android.graphics.Rect as AndroidRect

private val VideoAspect = Rational(16, 9)

/** System picture-in-picture: auto-enters when the app leaves while [autoEnter] holds. */
@Stable
class PictureInPictureState(private val activity: ComponentActivity?) {
    var isActive by mutableStateOf(activity?.isInPictureInPictureMode == true)
        internal set

    val isSupported: Boolean =
        activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true

    internal var sourceRect: AndroidRect? = null

    fun enter() {
        if (!isSupported) return
        activity?.enterPictureInPictureMode(params(autoEnter = true))
    }

    internal fun update(autoEnter: Boolean) {
        if (!isSupported) return
        activity?.setPictureInPictureParams(params(autoEnter))
    }

    private fun params(autoEnter: Boolean): PictureInPictureParams = PictureInPictureParams
        .Builder()
        .setAspectRatio(VideoAspect)
        .setAutoEnterEnabled(autoEnter)
        .setSeamlessResizeEnabled(true)
        .apply { sourceRect?.let(::setSourceRectHint) }
        .build()
}

@Composable
fun rememberPictureInPicture(autoEnter: Boolean, sourceRect: AndroidRect?): PictureInPictureState {
    val activity = LocalActivity.current as? ComponentActivity
    val state = remember(activity) { PictureInPictureState(activity) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> {
            state.isActive = it.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
            state.update(autoEnter = false)
        }
    }
    LaunchedEffect(autoEnter, sourceRect) {
        state.sourceRect = sourceRect
        state.update(autoEnter)
    }
    return state
}

/** Hides the status and navigation bars while [hidden]; a swipe shows them briefly. */
@Composable
fun SystemBarsHidden(hidden: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, hidden) {
        val controller = activity.window.insetsController
        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsets.Type.systemBars())
        }
        onDispose { if (hidden) controller?.show(WindowInsets.Type.systemBars()) }
    }
}

/**
 * The phone's full-screen button: forces landscape (or portrait to leave) and hands the
 * orientation back to the sensor once the device is physically held that way.
 */
@Stable
class OrientationLock(private val activity: ComponentActivity?) {
    private var target: Boolean? = null
    private val listener =
        activity?.let {
            object : OrientationEventListener(it) {
                override fun onOrientationChanged(degrees: Int) {
                    val landscape = target ?: return
                    if (degrees == ORIENTATION_UNKNOWN) return
                    val heldLandscape = degrees in LANDSCAPE_RIGHT || degrees in LANDSCAPE_LEFT
                    val heldPortrait = degrees < PORTRAIT_MARGIN || degrees > 360 - PORTRAIT_MARGIN
                    if ((landscape && heldLandscape) || (!landscape && heldPortrait)) release()
                }
            }
        }

    fun force(landscape: Boolean) {
        val host = activity ?: return
        target = landscape
        host.requestedOrientation =
            if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        listener?.enable()
    }

    fun release() {
        target = null
        listener?.disable()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private companion object {
        val LANDSCAPE_RIGHT = 60..120
        val LANDSCAPE_LEFT = 240..300
        const val PORTRAIT_MARGIN = 30
    }
}

@Composable
fun rememberOrientationLock(): OrientationLock {
    val activity = LocalActivity.current as? ComponentActivity
    val lock = remember(activity) { OrientationLock(activity) }
    DisposableEffect(lock) { onDispose { lock.release() } }
    return lock
}
