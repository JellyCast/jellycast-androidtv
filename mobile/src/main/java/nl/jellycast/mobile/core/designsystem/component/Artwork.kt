package nl.jellycast.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush
import nl.jellycast.mobile.core.designsystem.theme.ProgressTrack

val CardShape = RoundedCornerShape(8.dp)

const val POSTER_RATIO = 2f / 3f
const val LANDSCAPE_RATIO = 16f / 9f

/** A missing image is a flat block with the title, never a broken-image icon. */
@Composable
fun ArtworkImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Box(
        modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url == null) {
            TitleFallback(title)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { TitleFallback(title) },
                colorFilter =
                    if (dimmed) {
                        androidx.compose.ui.graphics.ColorFilter.tint(
                            Color.Black.copy(alpha = 0.45f),
                            androidx.compose.ui.graphics.BlendMode.Darken,
                        )
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun BoxScope.TitleFallback(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(8.dp),
    )
}

@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    dimmed: Boolean = false,
) {
    Box(modifier.aspectRatio(POSTER_RATIO)) {
        ArtworkImage(url, title, Modifier.fillMaxSize(), dimmed = dimmed)
        progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
fun LandscapeImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    dimmed: Boolean = false,
) {
    Box(modifier.aspectRatio(LANDSCAPE_RATIO)) {
        ArtworkImage(url, title, Modifier.fillMaxSize(), dimmed = dimmed)
        progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                drawRect(ProgressTrack)
                drawRect(
                    ProgressBrush,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                )
            },
    )
}
