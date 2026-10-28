package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size

const val POSTER_FADE_MS = 100
const val BACKDROP_FADE_MS = 200

private const val FALLBACK_TITLE_MAX_LINES = 3

/**
 * Coil draws the placeholder itself and drops it once the image is in, so no flat colour stays
 * underneath the image as overdraw.
 *
 * Turn [keepInMemory] off for backdrops: they are large and would push posters and logos out of
 * the memory cache while browsing. A backdrop fades in after a pause anyway, so decoding it again
 * from disk goes unnoticed.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    fadeMs: Int = POSTER_FADE_MS,
    colorFilter: ColorFilter? = null,
    showPlaceholder: Boolean = true,
    keepInMemory: Boolean = true,
    onSuccess: (() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    if (url == null) {
        Box(
            modifier = if (showPlaceholder) modifier.background(surface) else modifier,
            contentAlignment = Alignment.Center,
        ) {
            if (fallbackTitle != null) {
                Text(
                    text = fallbackTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = FALLBACK_TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        return
    }
    val context = LocalContext.current
    val request =
        remember(url, fadeMs, keepInMemory) {
            ImageRequest
                .Builder(context)
                .data(url)
                // The server already delivers the display size. A fixed size lets Coil draw a
                // memory-cached image in the same frame instead of waiting for layout first.
                .size(Size.ORIGINAL)
                .crossfade(fadeMs)
                .apply { if (!keepInMemory) memoryCachePolicy(CachePolicy.DISABLED) }
                .build()
        }
    val placeholder = if (showPlaceholder) remember(surface) { ColorPainter(surface) } else null
    AsyncImage(
        model = request,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = colorFilter,
        onSuccess = onSuccess?.let { callback -> { callback() } },
        modifier = modifier,
    )
}
