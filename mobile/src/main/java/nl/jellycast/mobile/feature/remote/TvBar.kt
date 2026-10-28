package nl.jellycast.mobile.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.common.Clock
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.RemoteSessions

/**
 * Polls the server while the bar is on screen, which is whenever the app is in the foreground,
 * and opens the now-playing screen when the app opens while a TV plays.
 */
class TvBarViewModel(private val remote: RemoteSessions, clock: Clock) : ViewModel() {
    val uiState: StateFlow<RemoteSession?> =
        channelFlow {
            launch { remote.poll() }
            remote.state.collect { send(it.current) }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    private val autoOpen = NowPlayingAutoOpen(clock)
    private var isPlayerOpen = false

    init {
        viewModelScope.launch {
            remote.state.collect { state ->
                autoOpen.sessionToOpen(state, isPlayerOpen)?.let(remote::show)
            }
        }
    }

    fun onOpen(session: RemoteSession) = remote.show(session)

    fun onAppOpened() = autoOpen.onAppOpened()

    fun onPlayerOpenChanged(isOpen: Boolean) {
        isPlayerOpen = isOpen
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}

private val BarHeight = 56.dp
private val StillWidth = 48.dp
private val StillHeight = 32.dp
private val ProgressInset = 24.dp
private val ProgressHeight = 2.dp

// The muted text color is too dark on the bar over a bright still.
private const val DETAIL_ALPHA = 0.8f

/** What another device plays, where, and how far; a tap opens the now-playing screen. */
@Composable
fun TvBarContent(session: RemoteSession, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val nowMs by rememberNowMs()
    val where = whereLine(session, nowMs)
    val hint = stringResource(R.string.remote_shows_what_plays_on, session.deviceName)
    Surface(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(CircleShape)
            .pressable(onOpen)
            .semantics { contentDescription = "${remoteTitle(session.item)}, $where. $hint" },
        shape = CircleShape,
        color = JellyCastColors.Rail,
    ) {
        Box {
            Row(
                Modifier.fillMaxHeight().padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteImage(
                    session.item.thumbUrl,
                    Modifier.size(StillWidth, StillHeight).clip(CircleShape),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        remoteTitle(session.item),
                        style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = JellyCastColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        where,
                        style = JellyCastText.Caption.copy(fontWeight = FontWeight.Medium),
                        color = JellyCastColors.Text.copy(alpha = DETAIL_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_tv),
                    contentDescription = null,
                    Modifier.size(20.dp),
                    tint = JellyCastColors.Accent,
                )
            }
            RemoteProgressLine(
                progress = { session.progressAt(nowMs) },
                height = ProgressHeight,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = ProgressInset),
            )
        }
    }
}

@Preview
@Composable
private fun TvBarPreview() {
    TvBarContent(PreviewSession, onOpen = {})
}
