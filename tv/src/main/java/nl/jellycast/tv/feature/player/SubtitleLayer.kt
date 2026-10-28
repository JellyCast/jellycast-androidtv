package nl.jellycast.tv.feature.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import nl.jellycast.core.playback.SubtitleSize

private const val SUBTITLE_BACKGROUND = 0x99000000.toInt()

/** `ContentFrame` only draws video; Media3's `SubtitleView` draws text, PGS and VobSub. */
@OptIn(UnstableApi::class)
@Composable
fun SubtitleLayer(
    player: Player?,
    size: SubtitleSize,
    background: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = remember { SubtitleView(context) }
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
    AndroidView(
        factory = { view },
        update = {
            it.setApplyEmbeddedStyles(true)
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size.scale)
            it.setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    if (background) SUBTITLE_BACKGROUND else Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null,
                ),
            )
        },
        modifier = modifier,
    )
}
