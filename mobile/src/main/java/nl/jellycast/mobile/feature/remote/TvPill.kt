package nl.jellycast.mobile.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.remote.RemoteSession

private val PillHeight = 40.dp
private val StillWidth = 36.dp
private val StillHeight = 24.dp
private val TitleMaxWidth = 220.dp
private val RingStill = 30.dp
private val RingWidth = 2.5.dp

/**
 * The TV bar on a tablet: a pill in the tab bar beside the tabs, so the bottom corner stays free
 * for the mini player. With [showsTitle] the still, the title and a progress line; without, the
 * still inside a progress ring. A pause sign covers the still while paused.
 */
@Composable
fun TvPillContent(
    session: RemoteSession,
    showsTitle: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs by rememberNowMs()
    val where = whereLine(session, nowMs)
    val hint = stringResource(R.string.remote_shows_what_plays_on, session.deviceName)
    Surface(
        modifier
            .height(PillHeight)
            .clip(CircleShape)
            .pressable(onOpen)
            .semantics { contentDescription = "${remoteTitle(session.item)}, $where. $hint" },
        shape = CircleShape,
        color = JellyCastColors.Rail,
    ) {
        if (showsTitle) {
            Row(
                Modifier.fillMaxHeight().padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillStill(session, Modifier.size(StillWidth, StillHeight))
                // Under the title only: across the still it ran into the pill's rounded ends.
                Box(Modifier.widthIn(max = TitleMaxWidth).width(IntrinsicSize.Max)) {
                    Text(
                        remoteTitle(session.item),
                        style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = JellyCastColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    RemoteProgressLine(
                        progress = { session.progressAt(nowMs) },
                        height = 2.dp,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .size(PillHeight)
                    .drawWithContent {
                        drawContent()
                        val stroke = RingWidth.toPx()
                        drawArc(
                            JellyCastColors.Accent,
                            startAngle = -90f,
                            sweepAngle = 360f * session.progressAt(nowMs).coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                PillStill(session, Modifier.size(RingStill))
            }
        }
    }
}

@Composable
private fun PillStill(session: RemoteSession, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        RemoteImage(session.item.thumbUrl, Modifier.matchParentSize())
        if (session.isPaused) {
            Icon(
                painterResource(R.drawable.ic_remote_pause),
                contentDescription = null,
                Modifier.size(14.dp),
                tint = JellyCastColors.Text,
            )
        }
    }
}

@Preview
@Composable
private fun TvPillPreview() {
    TvPillContent(PreviewSession, showsTitle = true, onOpen = {})
}

@Preview
@Composable
private fun TvPillRingPreview() {
    TvPillContent(PreviewSession.copy(isPaused = true), showsTitle = false, onOpen = {})
}
