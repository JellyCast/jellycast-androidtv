package nl.jellycast.mobile.ui.downloads

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadedMedia

/**
 * The looks of the download button: not downloaded, waiting, running with its progress,
 * waiting for the network, paused, failed, and on this device.
 */
@Composable
fun DownloadStateIcon(
    download: Download?,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    isPreparing: Boolean = false,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(JellyCastColors.Rail),
        contentAlignment = Alignment.Center,
    ) {
        val status = download?.status
        when {
            isPreparing ->
                CircularProgressIndicator(
                    Modifier.size(size * PREPARING_SCALE),
                    color = JellyCastColors.Text,
                    strokeWidth = 2.dp,
                )

            status == null -> Symbol(R.drawable.ic_downloads_to_device, 15.dp)

            status == DownloadStatus.Done ->
                Symbol(R.drawable.ic_check, 16.dp, JellyCastColors.Accent)

            status == DownloadStatus.Queued -> {
                Ring(fraction = null, dashed = true)
                Symbol(R.drawable.ic_downloads_clock, 13.dp)
            }

            status == DownloadStatus.Running -> {
                Ring(fraction = download.fraction ?: 0f)
                Symbol(R.drawable.ic_downloads_stop, 11.dp)
            }

            status == DownloadStatus.WaitingForNetwork -> {
                Ring(fraction = download.fraction ?: 0f)
                Symbol(R.drawable.ic_downloads_wifi_off, 12.dp)
            }

            status == DownloadStatus.Paused -> {
                Ring(fraction = download.fraction ?: 0f)
                Symbol(R.drawable.ic_downloads_arrow_down, 14.dp)
            }

            else -> Symbol(R.drawable.ic_downloads_exclamation, 14.dp)
        }
    }
}

@Composable
private fun Symbol(@DrawableRes icon: Int, size: Dp, tint: Color = JellyCastColors.Text) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(size), tint = tint)
}

@Composable
private fun Ring(fraction: Float?, dashed: Boolean = false) {
    Canvas(Modifier.fillMaxSize().padding(RingInset)) {
        val width = RingWidth.toPx()
        val track =
            Stroke(
                width = width,
                pathEffect =
                    if (dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.5.dp.toPx()))
                    } else {
                        null
                    },
            )
        drawCircle(JellyCastColors.Text.copy(alpha = TRACK_ALPHA), style = track)
        if (fraction != null && fraction > 0f) {
            drawArc(
                JellyCastColors.Accent,
                startAngle = -90f,
                sweepAngle = FULL_CIRCLE * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}

private val RingInset = 5.dp
private val RingWidth = 2.5.dp
private const val TRACK_ALPHA = 0.2f
private const val FULL_CIRCLE = 360f
private const val PREPARING_SCALE = 0.45f

@Preview
@Composable
private fun DownloadStateIconPreview() {
    val media = DownloadedMedia(id = "1", title = "Carrie", seriesId = "s", seasonNumber = 1)
    Row(Modifier.background(JellyCastColors.Background).padding(16.dp)) {
        DownloadStateIcon(null)
        DownloadStateIcon(Download(media, DownloadKind.Compact))
        DownloadStateIcon(
            Download(
                media,
                DownloadKind.Compact,
                DownloadStatus.Running,
                mediaSeconds = 42.0,
                totalMediaSeconds = 100.0,
            ),
        )
        DownloadStateIcon(Download(media, DownloadKind.Compact, DownloadStatus.Done))
    }
}
