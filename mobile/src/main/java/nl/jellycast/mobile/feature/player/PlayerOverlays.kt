package nl.jellycast.mobile.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.SegmentType
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassBorder
import nl.jellycast.mobile.core.designsystem.component.GlassButton
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText

private const val BUBBLE_WIDTH_FRACTION = 0.4f
private const val BUBBLE_FADE_MS = 150

/** Black with a large spinner, until the first frame. */
@Composable
fun StartOverlay(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/** iOS `PlayerErrorView`: triangle, message, "Try again" and maybe "Play via the server". */
@Composable
fun PlayerErrorView(
    message: String,
    viaServerLabel: String?,
    onRetry: () -> Unit,
    onPlayViaServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_player_warning),
                null,
                Modifier.size(26.dp),
                tint = JellyCastColors.TextMuted,
            )
            Text(
                message,
                style = JellyCastText.Subheadline,
                color = JellyCastColors.Text,
                textAlign = TextAlign.Center,
            )
            GlassButton(stringResource(R.string.player_try_again), onRetry)
            if (viaServerLabel != null) GlassButton(viaServerLabel, onPlayViaServer)
        }
    }
}

@Composable
fun StallIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(
            stringResource(R.string.player_waiting),
            style = JellyCastText.Footnote.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f),
            ),
            color = Color.White,
        )
    }
}

/** The half-moon on the tapped side with the seconds of the run. */
@Composable
fun BoxScope.SeekBubble(run: SeekRun?, lastSide: TapSide, modifier: Modifier = Modifier) {
    val side = run?.side ?: lastSide
    AnimatedVisibility(
        visible = run != null,
        enter = fadeIn(tween(BUBBLE_FADE_MS)),
        exit = fadeOut(tween(BUBBLE_FADE_MS)),
        modifier =
            modifier
                .align(if (side == TapSide.Back) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxWidth(BUBBLE_WIDTH_FRACTION)
                .fillMaxHeight(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawBehind {
                    val width = size.width * 2
                    val height = size.height * 1.4f
                    val left = if (side == TapSide.Back) size.width - width else 0f
                    drawOval(
                        Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(left, (size.height - height) / 2),
                        size = Size(width, height),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ShadowedIcon(
                    if (side ==
                        TapSide.Back
                    ) {
                        R.drawable.ic_player_fast_rewind
                    } else {
                        R.drawable.ic_player_fast_forward
                    },
                    null,
                    20.dp,
                )
                Text(
                    stringResource(R.string.player_seconds, run?.offsetSeconds ?: 0),
                    style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
    }
}

/** A capsule 40 high on a translucent surface: the skip, next or slow-connection pill. */
@Composable
fun SegmentPillView(pill: SegmentPill, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, label) = pillContent(pill)
    Row(
        modifier
            .height(40.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = JellyCastColors.Text)
        Text(
            label,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun pillContent(pill: SegmentPill): Pair<Int, String> = when (pill) {
    is SegmentPill.SlowConnection ->
        R.drawable.ic_player_wifi_off to
            (
                pill.lowerLevel?.let { stringResource(R.string.player_slow_lower, it.shortName) }
                    ?: stringResource(R.string.player_slow)
                )

    is SegmentPill.Skip ->
        R.drawable.ic_player_skip_next to
            stringResource(
                when (pill.type) {
                    SegmentType.Recap -> R.string.player_skip_recap
                    SegmentType.Preview -> R.string.player_skip_preview
                    else -> R.string.player_skip_intro
                },
            )

    is SegmentPill.Next -> R.drawable.ic_player_fast_forward to nextLabel(pill)
}

@Composable
private fun nextLabel(pill: SegmentPill.Next): String = when (val item = pill.item) {
    is PlayableItem.MovieItem -> stringResource(R.string.player_next_list, item.movie.title)

    is PlayableItem.EpisodeItem -> {
        val code = episodeCode(item.episode.seasonNumber, item.episode.episodeNumber)
        if (pill.fromList) {
            stringResource(R.string.player_next_list_episode, item.episode.seriesTitle, code)
        } else {
            stringResource(R.string.player_next_episode_code, code)
        }
    }
}

@Composable
fun episodeCode(season: Int?, episode: Int?): String =
    stringResource(R.string.player_episode_code, season ?: 0, episode ?: 0)
