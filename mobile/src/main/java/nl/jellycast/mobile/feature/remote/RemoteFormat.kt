package nl.jellycast.mobile.feature.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlinx.coroutines.delay
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.SegmentType
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.remote.RemoteItem
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.ui.remainingText

private const val TICK_MS = 1_000L
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val MS_PER_SECOND = 1_000

/** The wall clock, once a second, for positions that run on between the TV's reports. */
@Composable
internal fun rememberNowMs(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(TICK_MS - System.currentTimeMillis() % TICK_MS)
        value = System.currentTimeMillis()
    }
}

/** "Melbourne · The Pacific", or the movie title. */
internal fun remoteTitle(item: RemoteItem): String =
    listOfNotNull(item.name.takeIf { it.isNotBlank() }, item.seriesName).joinToString(" · ")

@Composable
internal fun placeLine(session: RemoteSession): String = stringResource(
    if (session.isPaused) R.string.remote_paused_on else R.string.remote_playing_on,
    session.deviceName,
)

/** "Playing on Living room · 32 min left", or "Paused on …". */
@Composable
internal fun whereLine(session: RemoteSession, nowMs: Long): String {
    val remaining = session.remainingMsAt(nowMs)?.let { remainingText(it) }
    return listOfNotNull(placeLine(session), remaining).joinToString(" · ")
}

@Composable
internal fun playMethodLabel(method: PlayMethod?): String? = when (method) {
    PlayMethod.DirectPlay -> stringResource(R.string.remote_direct_play)
    PlayMethod.DirectStream -> stringResource(R.string.remote_direct_stream)
    PlayMethod.Transcode -> stringResource(R.string.remote_transcode)
    null -> null
}

@Composable
internal fun skipLabel(type: SegmentType): String = stringResource(
    when (type) {
        SegmentType.Recap -> R.string.remote_skip_recap
        SegmentType.Preview -> R.string.remote_skip_preview
        else -> R.string.remote_skip_intro
    },
)

/** "23:41", "1:02:05". */
internal fun clockText(ms: Long): String {
    val seconds = (ms / MS_PER_SECOND).toInt().coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}
