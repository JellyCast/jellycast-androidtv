package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

@Composable
fun PosterCard(
    imageUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    FocusCard(
        onClick = onClick,
        onFocus = onFocus,
        modifier = modifier.size(Dimens.PosterWidth, Dimens.PosterHeight),
        // ArtworkImage draws no placeholder here and the card draws no fill: no overdraw.
        containerColor = Color.Transparent,
    ) {
        ArtworkImage(
            url = imageUrl,
            fallbackTitle = title,
            showPlaceholder = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun LandscapeCard(
    imageUrl: String?,
    title: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.width(Dimens.LandscapeWidth)) {
        FocusCard(
            onClick = onClick,
            onFocus = onFocus,
            modifier = Modifier.size(Dimens.LandscapeWidth, Dimens.LandscapeHeight),
            containerColor = Color.Transparent,
            onLongClick = onLongClick,
        ) {
            ArtworkImage(url = imageUrl, fallbackTitle = title, modifier = Modifier.fillMaxSize())
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .progressBar(
                            progress,
                            track = JellyCastColors.Background.copy(alpha = 0.7f),
                        ),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Drawn locally rather than by the server (`percentPlayed`), because a progress parameter in the
 * image URL breaks the image cache.
 */
fun Modifier.progressBar(fraction: Float, track: Color): Modifier = drawWithCache {
    val brush = progressBrush(size.width)
    val radius = CornerRadius(size.height / 2)
    onDrawBehind {
        drawRoundRect(color = track, cornerRadius = radius)
        drawRoundRect(
            brush = brush,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = radius,
        )
    }
}

fun progressBrush(width: Float): Brush = Brush.horizontalGradient(
    0f to JellyCastColors.ProgressStart,
    0.7f to JellyCastColors.ProgressMid,
    1f to JellyCastColors.ProgressEnd,
    endX = width,
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun CardsPreview() {
    JellyCastTheme {
        Column {
            PosterCard(imageUrl = null, title = "The Last of Us", onClick = {})
            LandscapeCard(
                imageUrl = null,
                title = "The Bear",
                label = "S3 · E4",
                progress = 0.45f,
                onClick = {},
            )
        }
    }
}
