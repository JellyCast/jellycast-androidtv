package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

private const val CORNER_TEXTURE_SCALE = 2f

/** Displayed at 720×405 dp in the top-right corner. */
@Composable
fun CornerBackdrop(url: String?, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val scrim = rememberScrim(url)
    Box(
        modifier =
            modifier
                // Image and gradient render once into a half-resolution texture drawn at 2x, so
                // scrolling rows underneath composite one texture instead of re-running the
                // radial gradient per pixel. The lower resolution is invisible under the gradient.
                .size(width = 360.dp, height = 202.5.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = CORNER_TEXTURE_SCALE
                    scaleY = CORNER_TEXTURE_SCALE
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .drawWithCache {
                    val brush =
                        Brush.radialGradient(
                            0f to background.copy(alpha = 0.25f),
                            0.45f to background.copy(alpha = 0.55f),
                            0.75f to background.copy(alpha = 0.92f),
                            0.92f to background,
                            center = Offset(size.width, 0f),
                            radius = size.width * 1.1f,
                        )
                    val bottomFade =
                        Brush.verticalGradient(
                            BOTTOM_FADE_START to background.copy(alpha = 0f),
                            1f to background,
                        )
                    onDrawWithContent {
                        drawContent()
                        if (scrim.visible) {
                            drawRect(brush)
                            drawRect(bottomFade)
                        }
                    }
                },
    ) {
        ArtworkImage(
            url = url,
            fadeMs = BACKDROP_FADE_MS,
            keepInMemory = false,
            showPlaceholder = false,
            onSuccess = scrim.onImageLoaded,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val BOTTOM_FADE_START = 0.6f

/**
 * [baseColor] is drawn under the image (e.g. the spine color while the backdrop loads); it lives
 * in the same texture, so it costs no extra full-screen layer.
 */
@Composable
fun FullBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified,
) {
    val background = MaterialTheme.colorScheme.background
    val scrim = rememberScrim(url)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Image and gradient render once into a texture, so a focus change composites one
                // texture instead of drawing both over the full screen.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val brush =
                        Brush.horizontalGradient(
                            0f to background,
                            0.42f to background.copy(alpha = 0.94f),
                            0.64f to background.copy(alpha = 0.60f),
                            1f to background.copy(alpha = 0.20f),
                        )
                    onDrawWithContent {
                        if (baseColor.isSpecified) drawRect(baseColor)
                        drawContent()
                        if (baseColor.isSpecified || scrim.visible) drawRect(brush)
                    }
                },
    ) {
        ArtworkImage(
            url = url,
            fadeMs = BACKDROP_FADE_MS,
            keepInMemory = false,
            showPlaceholder = false,
            onSuccess = scrim.onImageLoaded,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The gradient appears only with the image: over the bare background it shows visible banding.
 * It switches on at once rather than fading, because a separate fade lags behind Coil's
 * crossfade. Read in the draw phase, so it never recomposes.
 */
private class BackdropScrim {
    var visible by mutableStateOf(false)
        private set

    val onImageLoaded: () -> Unit = { visible = true }
}

@Composable
private fun rememberScrim(url: String?): BackdropScrim = remember(url) { BackdropScrim() }
