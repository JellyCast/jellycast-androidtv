package nl.jellycast.tv.feature.player

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.neonFocus
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.model.formatDuration

/** Pass the seek to the player only once the key rests: every seek step costs decoder work. */
private const val SEEK_SETTLE_MS = 400L

private const val PROGRESS_TICK_MS = 1_000L
private val GradientHeight = 320.dp

/** Icon-row buttons, used to return focus after a menu closes. */
enum class OverlayButton { PlayPause, Audio, Subtitles, Info }

/**
 * Focus starts on the timebar; [jumpRequester] is the jump pill above it, reached with ↑.
 * [onToggleInfo] `null` hides the info button.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerOverlay(
    player: Player,
    title: String,
    subtitle: String?,
    hasNext: Boolean,
    requesters: OverlayRequesters,
    jumpRequester: FocusRequester?,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onToggleInfo: (() -> Unit)?,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = PROGRESS_TICK_MS)
    val playPause = rememberPlayPauseButtonState(player)
    val scrub = remember { ScrubState() }
    LaunchedEffect(scrub.targetMs) {
        val target = scrub.targetMs ?: return@LaunchedEffect
        delay(SEEK_SETTLE_MS)
        player.seekTo(target)
        scrub.targetMs = null
    }
    val durationMs = progress.durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    val positionMs =
        (scrub.targetMs ?: progress.currentPositionMs).coerceIn(0, maxOf(durationMs, 0))

    Box(modifier.fillMaxSize()) {
        BottomGradient(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical),
        ) {
            OverlayTitle(title, subtitle)
            Timebar(
                positionMs = positionMs,
                durationMs = durationMs,
                seek = scrub.pulse,
                modifier =
                    Modifier
                        .padding(top = 18.dp)
                        .focusRequester(requesters.timebar)
                        .focusProperties {
                            down = requesters.playPause
                            jumpRequester?.let { up = it }
                        }.onKeyEvent { event ->
                            scrub.onTimebarKey(event, player, durationMs) { playPause.onClick() }
                        },
            )
            TimeRow(positionMs, durationMs)
            ControlRow(
                player = player,
                playPause = playPause,
                hasNext = hasNext,
                requesters = requesters,
                onOpenAudio = onOpenAudio,
                onOpenSubtitles = onOpenSubtitles,
                onToggleInfo = onToggleInfo,
                onPlayNext = onPlayNext,
            )
        }
    }
}

@Composable
private fun BottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(GradientHeight)
            .drawWithCache {
                val brush =
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0f),
                        0.6f to Color.Black.copy(alpha = 0.7f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    )
                onDrawBehind { drawRect(brush) }
            },
    )
}

@Composable
private fun ColumnScope.OverlayTitle(title: String, subtitle: String?) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = JellyCastColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.02.em,
                ),
            color = JellyCastColors.Text.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TimeRow(positionMs: Long, durationMs: Long) {
    Row(
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TimeText(formatDuration(positionMs), alpha = 1f)
        TimeText(
            stringResource(
                R.string.player_remaining,
                formatDuration(durationMs - positionMs),
            ),
            alpha = 0.7f,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ControlRow(
    player: Player,
    playPause: PlayPauseButtonState,
    hasNext: Boolean,
    requesters: OverlayRequesters,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onToggleInfo: (() -> Unit)?,
    onPlayNext: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIcon(R.drawable.ic_player_back10, R.string.player_back_10, onClick = {
            player.seekBack()
        })
        Spacer(Modifier.size(6.dp))
        OverlayIcon(
            icon =
                if (playPause.showPlay) {
                    R.drawable.ic_player_play
                } else {
                    R.drawable.ic_player_pause
                },
            description = R.string.player_play_pause,
            onClick = playPause::onClick,
            modifier = Modifier.focusRequester(requesters.playPause),
        )
        Spacer(Modifier.size(6.dp))
        OverlayIcon(R.drawable.ic_player_forward10, R.string.player_forward_10, onClick = {
            player.seekForward()
        })
        Spacer(Modifier.weight(1f))
        OverlayIcon(
            icon = R.drawable.ic_player_audio,
            description = R.string.player_audio,
            onClick = onOpenAudio,
            modifier = Modifier.focusRequester(requesters.audio),
        )
        Spacer(Modifier.size(6.dp))
        OverlayIcon(
            icon = R.drawable.ic_player_subtitles,
            description = R.string.player_subtitles,
            onClick = onOpenSubtitles,
            modifier = Modifier.focusRequester(requesters.subtitles),
        )
        if (onToggleInfo != null) {
            Spacer(Modifier.size(6.dp))
            OverlayIcon(
                icon = R.drawable.ic_player_info,
                description = R.string.player_info,
                onClick = onToggleInfo,
                modifier = Modifier.focusRequester(requesters.info),
            )
        }
        if (hasNext) {
            Spacer(Modifier.size(6.dp))
            OverlayIcon(
                R.drawable.ic_player_next,
                R.string.player_next_episode,
                onClick = onPlayNext,
            )
        }
    }
}

/** Created by the player screen, so it can return focus to a button after a menu closes. */
class OverlayRequesters {
    val timebar = FocusRequester()
    val playPause = FocusRequester()
    val audio = FocusRequester()
    val subtitles = FocusRequester()
    val info = FocusRequester()

    fun of(button: OverlayButton): FocusRequester = when (button) {
        OverlayButton.PlayPause -> playPause
        OverlayButton.Audio -> audio
        OverlayButton.Subtitles -> subtitles
        OverlayButton.Info -> info
    }
}

@Composable
private fun TimeText(text: String, alpha: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = 0.04.em,
        ),
        color = JellyCastColors.Text.copy(alpha = alpha),
    )
}

@Composable
private fun OverlayIcon(
    @DrawableRes icon: Int,
    description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .neonFocus(CircleShape)
                .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = JellyCastColors.Text,
            modifier = Modifier.size(22.dp),
        )
    }
}
