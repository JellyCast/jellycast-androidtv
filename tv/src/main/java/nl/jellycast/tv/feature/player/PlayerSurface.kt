package nl.jellycast.tv.feature.player

import androidx.annotation.OptIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import nl.jellycast.core.playback.SubtitleSize

/**
 * The video, its padding-row mask and subtitles, plus the idle focus target. The video is on a
 * SurfaceView, which ignores alpha: never fade this layer.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSurface(
    player: Player?,
    subtitleSize: SubtitleSize,
    subtitleBackground: Boolean,
    idleRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize())
        PaddingRowMask(player = player, modifier = Modifier.fillMaxSize())
        SubtitleLayer(
            player = player,
            size = subtitleSize,
            background = subtitleBackground,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.size(1.dp).focusRequester(idleRequester).focusable())
    }
}
