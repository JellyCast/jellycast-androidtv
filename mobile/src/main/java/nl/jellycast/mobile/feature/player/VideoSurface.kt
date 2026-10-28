package nl.jellycast.mobile.feature.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame

private const val SUBTITLE_TEXT_FRACTION = 0.05f
private const val SUBTITLE_BOTTOM_FRACTION = 0.07f

/**
 * The picture (on a SurfaceView, which ignores alpha and scale: it is moved and sized by layout
 * only) and the subtitles. Composed at one place for the whole play, so the surface never
 * changes between the full and the mini player.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, showsSubtitles: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.background(ComposeColor.Black), contentAlignment = Alignment.Center) {
        ContentFrame(player = player)
        if (showsSubtitles) SubtitleLayer(player, Modifier.fillMaxSize())
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SubtitleLayer(player: Player?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view =
        remember {
            SubtitleView(context).apply {
                setApplyEmbeddedStyles(true)
                setFractionalTextSize(SUBTITLE_TEXT_FRACTION)
                setBottomPaddingFraction(SUBTITLE_BOTTOM_FRACTION)
                setStyle(
                    CaptionStyleCompat(
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        Color.BLACK,
                        null,
                    ),
                )
            }
        }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    view.setCues(cueGroup.cues)
                }
            }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            view.setCues(emptyList())
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
