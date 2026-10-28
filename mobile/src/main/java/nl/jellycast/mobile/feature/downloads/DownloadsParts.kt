package nl.jellycast.mobile.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush
import nl.jellycast.mobile.core.downloads.DownloadedMedia
import nl.jellycast.mobile.ui.downloads.DownloadText

/** Poster (the series' for an episode), three lines of text, and a trailing control. */
@Composable
internal fun DownloadRowLayout(
    media: DownloadedMedia,
    title: String,
    detail: String,
    status: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = if (media.isEpisode) media.seriesPosterUrl else media.posterUrl,
            title = media.seriesTitle ?: media.title,
            modifier = Modifier.size(width = PosterWidth, height = PosterHeight),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = JellyCastText.Headline,
                color = JellyCastColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
            Text(
                status,
                style = JellyCastText.Subheadline,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** Material's swipe to delete, from the end, as iOS swipe actions. */
@Composable
internal fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(RemoveColor).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_downloads_delete),
                    contentDescription = stringResource(R.string.downloads_remove),
                    tint = Color.White,
                )
            }
        },
        content = { Row(Modifier.background(JellyCastColors.Background)) { content() } },
    )
}

/** "2 movies · 3 episodes · 8.9 GB of 256 GB", with a bar of JellyCast, the rest and free space. */
@Composable
internal fun StorageSummaryView(
    summary: StorageSummary,
    text: DownloadText,
    modifier: Modifier = Modifier,
) {
    val used = text.bytes(summary.usedBytes)
    val space = summary.space
    Column(modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            DownloadText.joined(
                summary.movieCount.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.downloads_movies, it, it)
                },
                summary.episodeCount.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.downloads_episodes, it, it)
                },
                space?.let {
                    stringResource(
                        R.string.downloads_used_of_total,
                        used,
                        text.bytes(it.totalBytes),
                    )
                } ?: used,
            ),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.Text,
        )
        if (space != null && space.totalBytes > 0) {
            val other = summary.otherBytes ?: 0
            StorageBar(
                usedFraction = summary.usedBytes.toFloat() / space.totalBytes,
                otherFraction = other.toFloat() / space.totalBytes,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    stringResource(R.string.downloads_storage_app, used),
                    stringResource(R.string.downloads_storage_other, text.bytes(other)),
                    stringResource(R.string.downloads_storage_free, text.bytes(space.freeBytes)),
                ).forEach {
                    Text(it, style = JellyCastText.Caption, color = JellyCastColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun StorageBar(usedFraction: Float, otherFraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .drawBehind {
                val minUsed = MinUsedWidth.toPx()
                val usedWidth = (size.width * usedFraction).coerceAtLeast(minUsed)
                drawRect(ProgressBrush, size = Size(usedWidth, size.height))
                drawRect(
                    JellyCastColors.TextMuted.copy(alpha = OTHER_ALPHA),
                    topLeft = Offset(usedWidth, 0f),
                    size = Size(size.width * otherFraction, size.height),
                )
            },
    )
}

private val PosterWidth: Dp = 60.dp
private val PosterHeight: Dp = 90.dp
private val BarHeight = 6.dp
private val MinUsedWidth = 2.dp
private const val OTHER_ALPHA = 0.45f
private val RemoveColor = Color(0xFFD9443A)
