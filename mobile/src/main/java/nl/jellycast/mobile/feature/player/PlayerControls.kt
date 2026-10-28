package nl.jellycast.mobile.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.formatDuration
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText

private const val FADE_MS = 200
private val IconShadow = Color.Black.copy(alpha = 0.6f)
private val TextShadow = Shadow(IconShadow, blurRadius = 8f)

/** Title block of the full-screen controls: movie name and year, or series and episode. */
data class PlayerTitle(val title: String, val subtitle: String?)

/** Which top-row buttons show; the others are hidden, not disabled. */
data class ControlButtons(val pip: Boolean, val continueOn: Boolean)

class ControlActions(
    val onPip: () -> Unit,
    val onContinueOn: () -> Unit,
    val onTracks: () -> Unit,
    val onQuality: () -> Unit,
    val onPlayPause: () -> Unit,
    val onToggleFullScreen: () -> Unit,
    val onScrub: (Float) -> Unit,
    val onScrubEnd: () -> Unit,
)

/** iOS `PlayerControlsOverlay`: no scrim, shadowed white icons, fades in 0.2 s. */
@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    isFullScreen: Boolean,
    title: PlayerTitle?,
    engine: PlayerEngineState,
    scrubFraction: Float?,
    autoLabel: String?,
    buttons: ControlButtons,
    actions: ControlActions,
    modifier: Modifier = Modifier,
    safePadding: PaddingValues = PaddingValues(),
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(FADE_MS, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(FADE_MS, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().padding(controlsPadding(isFullScreen, safePadding))) {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.Top) {
                if (isFullScreen && title != null) {
                    TitleBlock(title, Modifier.weight(1f).padding(top = 6.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TopButtons(buttons, actions)
            }
            CentreButton(engine, actions.onPlayPause, Modifier.align(Alignment.Center))
            Column(Modifier.align(Alignment.BottomStart)) {
                if (isFullScreen) {
                    ScrubBar(
                        played = { scrubFraction ?: engine.playedFraction() },
                        buffered = { engine.bufferedFraction() },
                        interactive = true,
                        label = stringResource(R.string.player_position),
                        onScrub = actions.onScrub,
                        onScrubEnd = actions.onScrubEnd,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeLabel(engine, scrubFraction, Modifier.weight(1f))
                    if (autoLabel != null) {
                        Text(
                            autoLabel,
                            style = JellyCastText.Footnote.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = TextShadow,
                            ),
                            color = JellyCastColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                    PlayerIconButton(
                        icon =
                            if (isFullScreen) {
                                R.drawable.ic_player_fullscreen_exit
                            } else {
                                R.drawable.ic_player_fullscreen
                            },
                        contentDescription =
                            stringResource(
                                if (isFullScreen) R.string.player_exit_full_screen else R.string.player_full_screen,
                            ),
                        onClick = actions.onToggleFullScreen,
                        iconSize = 22.dp,
                    )
                }
            }
        }
    }
}

private fun controlsPadding(isFullScreen: Boolean, safe: PaddingValues): PaddingValues =
    if (isFullScreen) {
        PaddingValues(
            start = maxOf(safe.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), 20.dp),
            top = maxOf(safe.calculateTopPadding(), 12.dp),
            end = maxOf(safe.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), 20.dp),
            bottom = maxOf(safe.calculateBottomPadding(), 8.dp),
        )
    } else {
        PaddingValues(start = 14.dp, top = 4.dp, end = 6.dp, bottom = 6.dp)
    }

@Composable
private fun TopButtons(buttons: ControlButtons, actions: ControlActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (buttons.pip) {
            PlayerIconButton(
                R.drawable.ic_player_pip,
                stringResource(R.string.player_pip),
                actions.onPip,
            )
        }
        if (buttons.continueOn) {
            PlayerIconButton(
                R.drawable.ic_player_cast,
                stringResource(R.string.player_continue_on),
                actions.onContinueOn,
            )
        }
        PlayerIconButton(
            R.drawable.ic_player_subtitles,
            stringResource(R.string.player_tracks),
            actions.onTracks,
        )
        PlayerIconButton(
            R.drawable.ic_settings,
            stringResource(R.string.player_quality),
            actions.onQuality,
        )
    }
}

@Composable
private fun TitleBlock(title: PlayerTitle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title.title,
            style = JellyCastText.Headline.copy(shadow = TextShadow),
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (title.subtitle != null) {
            Text(
                title.subtitle,
                style = JellyCastText.Footnote.copy(shadow = TextShadow),
                color = JellyCastColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CentreButton(engine: PlayerEngineState, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .pressable(onPlayPause),
        contentAlignment = Alignment.Center,
    ) {
        if (engine.isStalled) {
            val waitingLabel = stringResource(R.string.player_waiting)
            CircularProgressIndicator(
                Modifier.size(32.dp).semantics { contentDescription = waitingLabel },
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else {
            ShadowedIcon(
                if (engine.playWhenReady) R.drawable.ic_player_pause else R.drawable.ic_play,
                stringResource(R.string.player_play_pause),
                28.dp,
            )
        }
    }
}

@Composable
private fun TimeLabel(engine: PlayerEngineState, scrubFraction: Float?, modifier: Modifier = Modifier) {
    val duration = engine.durationMs
    val position = scrubFraction?.let { (it * duration).toLong() } ?: engine.positionMs
    val ahead = (engine.bufferedMs - engine.positionMs).coerceAtLeast(0)
    val buffered = stringResource(R.string.player_buffered, formatDuration(ahead))
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(color = JellyCastColors.Text)) {
                append(if (duration > 0) formatDuration(position) else UNKNOWN_TIME)
            }
            withStyle(SpanStyle(color = JellyCastColors.TextMuted)) {
                append(" / ")
                append(if (duration > 0) formatDuration(duration) else UNKNOWN_TIME)
                if (duration > 0) append(" $buffered")
            }
        }
    Text(
        text,
        style = TimeStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private const val UNKNOWN_TIME = "--:--"

private val TimeStyle: TextStyle =
    JellyCastText.Footnote.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        shadow = TextShadow,
    )

/** A plain white icon in a 44 dp hit box, with a soft black shadow; pressed dims to 70%. */
@Composable
fun PlayerIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 19.dp,
) {
    Box(modifier.size(44.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        ShadowedIcon(icon, contentDescription, iconSize)
    }
}

@Composable
fun ShadowedIcon(@DrawableRes icon: Int, contentDescription: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(size).offset(y = 1.dp),
            tint = IconShadow,
        )
        Icon(painterResource(icon), contentDescription, Modifier.size(size), tint = Color.White)
    }
}

fun PlayerEngineState.playedFraction(): Float =
    if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

fun PlayerEngineState.bufferedFraction(): Float =
    if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f
