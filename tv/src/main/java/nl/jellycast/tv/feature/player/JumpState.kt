package nl.jellycast.tv.feature.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.nextCardStartMs
import nl.jellycast.core.model.skippableAt
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.ui.episodeCode

private const val END_CHECK_INTERVAL_MS = 1_000L
private const val SKIP_CHECK_INTERVAL_MS = 500L
private const val NEXT_COUNTDOWN_MS = 10_000L
private const val COUNTDOWN_TICK_MS = 100L

/** Bottom offset of the pill while the overlay is visible: just above the timebar. */
private val JumpAboveTimebar = 132.dp

/** Jump pill state for one plan: a segment to skip, or the next episode near the end. */
internal class JumpState {
    var nextDismissed by mutableStateOf(false)
    var nearEnd by mutableStateOf(false)
    var skipSegment by mutableStateOf<MediaSegment?>(null)
    var dismissedSkip by mutableStateOf<MediaSegment?>(null)
    var countdownLeftMs by mutableLongStateOf(NEXT_COUNTDOWN_MS)

    val countdownProgress: Float
        get() = 1f - countdownLeftMs.toFloat() / NEXT_COUNTDOWN_MS

    fun dismissSkip(segment: MediaSegment) {
        dismissedSkip = segment
        skipSegment = null
    }

    fun skipped(segment: MediaSegment, segments: List<MediaSegment>) {
        dismissedSkip = segment
        // A segment that starts right where this one ends (recap → intro) keeps the pill and focus.
        skipSegment = segments.skippableAt(segment.endMs)
    }
}

@Composable
internal fun JumpEffects(
    jump: JumpState,
    player: Player?,
    nextEpisode: Episode?,
    segments: List<MediaSegment>,
    onNearEnd: () -> Unit,
) {
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    LaunchedEffect(player, nextEpisode, segments) {
        val activePlayer = player ?: return@LaunchedEffect
        if (nextEpisode == null) return@LaunchedEffect
        while (isActive) {
            val duration = activePlayer.duration
            jump.nearEnd =
                duration != C.TIME_UNSET &&
                activePlayer.currentPosition >= segments.nextCardStartMs(duration)
            delay(END_CHECK_INTERVAL_MS)
        }
    }

    LaunchedEffect(player, segments) {
        val activePlayer = player ?: return@LaunchedEffect
        if (segments.isEmpty()) return@LaunchedEffect
        while (isActive) {
            jump.skipSegment =
                segments
                    .skippableAt(activePlayer.currentPosition)
                    ?.takeIf { it != jump.dismissedSkip }
            delay(SKIP_CHECK_INTERVAL_MS)
        }
    }

    // In the credits: wake the server's disk and fetch the next plan ahead of time.
    val nearEnd = jump.nearEnd
    LaunchedEffect(nearEnd) { if (nearEnd) currentOnNearEnd() }
}

/** Counts down to the next episode; pauses while the overlay is visible. */
@Composable
internal fun NextEpisodeCountdown(
    jump: JumpState,
    showNextPill: Boolean,
    overlayVisible: Boolean,
    onFinish: () -> Unit,
) {
    val currentOnFinished by rememberUpdatedState(onFinish)
    LaunchedEffect(showNextPill, overlayVisible) {
        if (!showNextPill || overlayVisible) return@LaunchedEffect
        while (jump.countdownLeftMs > 0) {
            delay(COUNTDOWN_TICK_MS)
            jump.countdownLeftMs -= COUNTDOWN_TICK_MS
        }
        currentOnFinished()
    }
}

/** Shows [nextEpisode] when it is set, otherwise [skip]. */
@Composable
internal fun PlayerJumpPill(
    nextEpisode: Episode?,
    skip: MediaSegment?,
    overlayVisible: Boolean,
    focusRequester: FocusRequester,
    countdownProgress: () -> Float,
    onPlayNext: () -> Unit,
    onSkip: (MediaSegment) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillModifier =
        modifier
            .padding(
                end = Dimens.SafeHorizontal,
                bottom = if (overlayVisible) JumpAboveTimebar else Dimens.SafeVertical,
            ).onFocusChanged { if (it.isFocused) onFocus() }
    if (nextEpisode != null) {
        JumpPill(
            title =
                "${episodeCode(nextEpisode.seasonNumber, nextEpisode.episodeNumber)}  " +
                    nextEpisode.title,
            label = stringResource(R.string.player_next_episode),
            onClick = onPlayNext,
            focusRequester = focusRequester,
            countdown = countdownProgress,
            modifier = pillModifier,
        )
    } else if (skip != null) {
        JumpPill(
            title = stringResource(skip.type.skipLabel()),
            onClick = { onSkip(skip) },
            focusRequester = focusRequester,
            modifier = pillModifier,
        )
    }
}
