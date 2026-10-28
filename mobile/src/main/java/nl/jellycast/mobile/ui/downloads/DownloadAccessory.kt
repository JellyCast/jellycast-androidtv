package nl.jellycast.mobile.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassBorder
import nl.jellycast.mobile.core.designsystem.component.WatchProgressBar
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.downloads.DownloadStore
import org.koin.compose.koinInject

/**
 * The running download in the bar above the tab bar: "The Pitt · S1 · E3", its status and a
 * progress line. Draws nothing when no download runs.
 */
@Composable
fun DownloadAccessory(onOpenDownloads: () -> Unit, modifier: Modifier = Modifier) {
    val store: DownloadStore = koinInject()
    val download by store.current.collectAsStateWithLifecycle()
    val rate by store.rate.collectAsStateWithLifecycle()
    val running = download ?: return
    val text = rememberDownloadText()
    val hint = stringResource(R.string.downloads_shows_downloads)
    Box(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(CircleShape)
            .background(JellyCastColors.Surface)
            .border(1.dp, GlassBorder, CircleShape)
            .semantics {
                onClick(label = hint) {
                    onOpenDownloads()
                    true
                }
            }
            .pressable(onOpenDownloads),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp).align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadStateIcon(running, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text.title(running.media),
                    style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = JellyCastColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text.statusLine(running, store.secondsLeft(running, rate)),
                    style = JellyCastText.Caption.copy(fontWeight = FontWeight.Medium),
                    color = JellyCastColors.Text.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        running.fraction?.let {
            WatchProgressBar(
                progress = it,
                onImage = true,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = ProgressInset)
                        .padding(bottom = 4.dp),
            )
        }
    }
}

private val BarHeight = 56.dp
private val ProgressInset = 24.dp
