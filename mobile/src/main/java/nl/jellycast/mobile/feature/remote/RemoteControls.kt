package nl.jellycast.mobile.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.skippableAt
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.androidtv.TvKey
import nl.jellycast.mobile.core.remote.androidtv.TvVolume
import nl.jellycast.mobile.core.ui.remainingText

private const val SKIP_MS = 10_000L
private const val VOLUME_SHOWN_MS = 2_000L

/** Close, which device, its audio and subtitles, and stop. */
@Composable
internal fun RemoteTopBar(
    session: RemoteSession,
    hasTracks: Boolean,
    volume: TvVolumeState,
    onClose: () -> Unit,
    onTracks: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsVolume by remember { mutableStateOf(false) }
    LaunchedEffect(volume.changedAtMs) {
        if (volume.changedAtMs == null) return@LaunchedEffect
        showsVolume = true
        delay(VOLUME_SHOWN_MS)
        showsVolume = false
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RemoteGlassButton(
                R.drawable.ic_remote_expand_more,
                stringResource(R.string.remote_close),
                onClose,
            )
            Spacer(Modifier.weight(1f))
            if (session.item.seriesName != null) {
                RemoteIconButton(
                    R.drawable.ic_remote_skip_next,
                    stringResource(R.string.remote_next_episode),
                    { onCommand(RemoteCommand.NextEpisode) },
                )
            }
            if (hasTracks && session.playMethod == PlayMethod.DirectPlay) {
                RemoteIconButton(
                    R.drawable.ic_remote_subtitles,
                    stringResource(R.string.remote_audio_subtitles),
                    onTracks,
                )
            }
            RemoteIconButton(
                R.drawable.ic_remote_stop,
                stringResource(R.string.remote_stop_on_tv),
                { onCommand(RemoteCommand.Stop) },
            )
        }
        val level = volume.volume
        Box(Modifier.padding(horizontal = TitleInset)) {
            if (showsVolume && level != null) {
                TvVolumeLevel(level)
            } else {
                DeviceLabel(session.deviceName)
            }
        }
    }
}

// Keeps the device name clear of the buttons on both sides.
private val TitleInset = 136.dp

@Composable
private fun DeviceLabel(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_tv), null, Modifier.size(16.dp), JellyCastColors.Accent)
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The TV's volume for a moment after a press, in place of the device name. */
@Composable
private fun TvVolumeLevel(volume: TvVolume) {
    val label = stringResource(R.string.remote_tv_volume, volume.level)
    Row(
        Modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(
                if (volume.isMuted) {
                    R.drawable.ic_remote_volume_off
                } else {
                    R.drawable.ic_remote_volume_up
                },
            ),
            null,
            Modifier.size(16.dp),
            JellyCastColors.Accent,
        )
        Spacer(Modifier.width(8.dp))
        if (volume.max > 0) {
            RemoteProgressLine(
                progress = { volume.level.toFloat() / volume.max },
                modifier = Modifier.width(64.dp),
            )
        } else {
            Text(
                volume.level.toString(),
                style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
            )
        }
    }
}

/** Back 10 s, play or pause, forward 10 s; with the TV's volume on the sides once it can be set. */
@Composable
internal fun RemoteTransport(
    session: RemoteSession,
    nowMs: () -> Long,
    showsVolume: Boolean,
    onCommand: (RemoteCommand) -> Unit,
    onVolumeKey: (TvKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun seekBy(deltaMs: Long) {
        val target = session.positionAt(nowMs()) + deltaMs
        val end = session.item.runtimeMs ?: target
        onCommand(RemoteCommand.Seek(target.coerceIn(0, end.coerceAtLeast(0))))
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (showsVolume) 26.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsVolume) {
            RemoteGlassButton(
                R.drawable.ic_remote_volume_down,
                stringResource(R.string.remote_volume_down),
                { onVolumeKey(TvKey.VolumeDown) },
                size = 40.dp,
                iconSize = 18.dp,
            )
        }
        SkipButton(stringResource(R.string.remote_back_10), mirrored = false) { seekBy(-SKIP_MS) }
        PlayPauseButton(session, 68.dp, 30.dp, onCommand)
        SkipButton(stringResource(R.string.remote_forward_10), mirrored = true) { seekBy(SKIP_MS) }
        if (showsVolume) {
            RemoteGlassButton(
                R.drawable.ic_remote_volume_up,
                stringResource(R.string.remote_volume_up),
                { onVolumeKey(TvKey.VolumeUp) },
                size = 40.dp,
                iconSize = 18.dp,
            )
        }
    }
}

@Composable
internal fun PlayPauseButton(
    session: RemoteSession,
    size: Dp,
    iconSize: Dp,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteGlassButton(
        icon = if (session.isPaused) R.drawable.ic_play else R.drawable.ic_remote_pause,
        contentDescription =
            stringResource(if (session.isPaused) R.string.remote_play else R.string.remote_pause),
        onClick = {
            onCommand(if (session.isPaused) RemoteCommand.Unpause else RemoteCommand.Pause)
        },
        modifier = modifier,
        size = size,
        iconSize = iconSize,
    )
}

/** The replay arrow with "10" in it; forward is the same arrow mirrored. */
@Composable
private fun SkipButton(label: String, mirrored: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pressable(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_remote_replay),
            null,
            Modifier.size(34.dp).graphicsLayer { scaleX = if (mirrored) -1f else 1f },
            JellyCastColors.Text,
        )
        Text(
            "10",
            style = JellyCastText.Caption2.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** The time and a bar to drag; the TV seeks once the finger lets go. */
@Composable
internal fun RemoteScrubber(
    session: RemoteSession,
    nowMs: Long,
    skippable: MediaSegment?,
    onSkip: (MediaSegment) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val runtime = session.item.runtimeMs ?: 0
    val fraction = scrubFraction ?: session.progressAt(nowMs)
    val position = scrubFraction?.let { (it * runtime).toLong() } ?: session.positionAt(nowMs)
    val skip = skippable.takeIf { scrubFraction == null }
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val paused = stringResource(R.string.remote_paused)
            Text(
                listOfNotNull(clockText(position), paused.takeIf { session.isPaused })
                    .joinToString(" · "),
                style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Medium),
                color = JellyCastColors.Text,
            )
            Spacer(Modifier.weight(1f))
            if (skip != null) {
                SkipSegmentPill(skipLabel(skip.type), onClick = { onSkip(skip) })
            } else if (runtime > 0) {
                Text(
                    remainingText((runtime - position).coerceAtLeast(0)),
                    style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Medium),
                    color = JellyCastColors.TextMuted,
                )
            }
        }
        ScrubBar(
            fraction = fraction,
            positionText = clockText(session.positionAt(nowMs)),
            onScrub = { scrubFraction = it },
            onRelease = {
                scrubFraction?.let { if (runtime > 0) onSeek((it * runtime).toLong()) }
                scrubFraction = null
            },
        )
    }
}

private val KnobSize = 14.dp

@Composable
private fun ScrubBar(
    fraction: Float,
    positionText: String,
    onScrub: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val knobPx = with(LocalDensity.current) { KnobSize.toPx() }
    val label = stringResource(R.string.remote_position)
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = label
                stateDescription = positionText
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        onScrub((offset.x / size.width).coerceIn(0f, 1f))
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, _ ->
                    onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        RemoteProgressLine(progress = { fraction })
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (widthPx * fraction.coerceIn(0f, 1f) - knobPx / 2).roundToInt(),
                        0,
                    )
                }.size(KnobSize)
                .clip(CircleShape)
                .background(JellyCastColors.Text),
        )
    }
}

/** For a session that can only be watched: the time and how far. */
@Composable
internal fun RemoteTimeline(session: RemoteSession, nowMs: Long, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                clockText(session.positionAt(nowMs)),
                style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Medium),
                color = JellyCastColors.Text,
            )
            Spacer(Modifier.weight(1f))
            session.remainingMsAt(nowMs)?.let {
                Text(
                    remainingText(it),
                    style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Medium),
                    color = JellyCastColors.TextMuted,
                )
            }
        }
        RemoteProgressLine(progress = { session.progressAt(nowMs) })
    }
}

/** The segment to offer skipping, unless the user just skipped it. */
internal fun skippableSegment(
    segments: List<MediaSegment>,
    positionMs: Long,
    skippedStartMs: Long?,
): MediaSegment? = segments.skippableAt(positionMs)?.takeIf { it.startMs != skippedStartMs }
