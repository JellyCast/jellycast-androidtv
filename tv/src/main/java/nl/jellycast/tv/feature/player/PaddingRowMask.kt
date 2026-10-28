package nl.jellycast.tv.feature.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlin.math.ceil
import kotlin.math.min

private const val CODED_ROW_ALIGNMENT = 8

/** Leaves a fraction of slack, so an exact size does not cost a whole pixel of picture. */
private const val ROUNDING_SLACK_PX = 0.01f

/**
 * Pixels to cover at the bottom of the picture. A height that is not a multiple of 8 is padded by
 * the encoder; the decoder should crop those rows, but some compositors (Shield, Fire TV) show
 * them as a line under the picture (Moonfin mf#1667).
 */
internal fun paddingMaskHeightPx(sourceHeight: Int, displayedHeight: Float): Int {
    if (sourceHeight <= 0 || displayedHeight <= 0f) return 0
    val padded = (CODED_ROW_ALIGNMENT - sourceHeight % CODED_ROW_ALIGNMENT) % CODED_ROW_ALIGNMENT
    if (padded == 0) return 0
    return ceil(padded * displayedHeight / sourceHeight - ROUNDING_SLACK_PX)
        .toInt()
        .coerceAtLeast(1)
}

/** Black strip over the padded rows, scaled like `ContentFrame` (fit, centered). */
@Composable
fun PaddingRowMask(player: Player?, modifier: Modifier = Modifier) {
    var videoSize by remember(player) { mutableStateOf(player?.videoSize ?: VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }
            }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    Spacer(
        modifier.drawBehind {
            val width = videoSize.width * videoSize.pixelWidthHeightRatio
            val height = videoSize.height.toFloat()
            if (width <= 0f || height <= 0f) return@drawBehind
            val scale = min(size.width / width, size.height / height)
            val shownWidth = width * scale
            val shownHeight = height * scale
            val mask = paddingMaskHeightPx(videoSize.height, shownHeight)
            if (mask == 0) return@drawBehind
            val bottom = (size.height + shownHeight) / 2
            drawRect(
                color = Color.Black,
                topLeft = Offset((size.width - shownWidth) / 2, bottom - mask),
                size = Size(shownWidth, mask.toFloat()),
            )
        },
    )
}
