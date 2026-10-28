package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.BACKDROP_FADE_MS
import nl.jellycast.tv.core.designsystem.component.neonFocus
import nl.jellycast.tv.core.designsystem.component.progressBar
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.ui.dynamicRangeLabel
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.resolutionLabel
import nl.jellycast.tv.core.ui.runtimeText

// While key repeat flips through movies the panel skips backdrop and text, so each step fits in
// a frame.
private const val PANEL_DETAILS_DELAY_MS = 300L

private const val GRADIENT_START_FRACTION = 0.25f

private val PanelButtonWidth = 260.dp

internal enum class PanelButton { Play, Details }

@Composable
internal fun MoviePanel(
    movie: Movie,
    panelButton: PanelButton,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    val spineColor = Color(movie.spine.color)
    val ink = movie.spine.ink()
    var showDetails by remember(movie.id) { mutableStateOf(false) }
    LaunchedEffect(movie.id) {
        delay(PANEL_DETAILS_DELAY_MS)
        showDetails = true
    }
    Box(
        modifier
            .size(BookcaseDimens.PanelWidth, BookcaseDimens.Height)
            .background(spineColor),
    ) {
        if (showDetails) {
            ArtworkImage(
                url = movie.artwork.backdrop,
                fadeMs = BACKDROP_FADE_MS,
                keepInMemory = false,
                showPlaceholder = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Only drawn over a backdrop: on the plain panel the gradient would just cost GPU time.
        if (showDetails) BackdropToSpineGradient(spineColor)
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, end = 28.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanelTitle(movie = movie, ink = ink)
            // Fixed height, so logo and buttons do not jump when the details appear.
            val progress = movie.progress.takeIf { showProgress }
            Column(
                modifier = Modifier.height(if (progress != null) 34.dp else 21.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showDetails) {
                    PanelMeta(movie = movie, showProgress = showProgress, ink = ink)
                    if (progress != null) {
                        Box(
                            Modifier
                                .width(PanelButtonWidth)
                                .height(3.dp)
                                .progressBar(
                                    progress,
                                    track = JellyCastColors.Text.copy(alpha = 0.2f),
                                ),
                        )
                    }
                }
            }
            PanelButtons(movie = movie, panelButton = panelButton)
        }
    }
}

@Composable
private fun BackdropToSpineGradient(spineColor: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val top = size.height * GRADIENT_START_FRACTION
                val brush =
                    Brush.verticalGradient(
                        0f to spineColor.copy(alpha = 0f),
                        0.44f to spineColor.copy(alpha = 0.85f),
                        0.71f to spineColor,
                        startY = top,
                        endY = size.height,
                    )
                onDrawBehind {
                    drawRect(
                        brush = brush,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                    )
                }
            },
    )
}

@Composable
private fun PanelTitle(movie: Movie, ink: SpineInk) {
    Box(Modifier.size(280.dp, 56.dp), contentAlignment = Alignment.CenterStart) {
        if (movie.artwork.logo != null) {
            ArtworkImage(
                url = movie.artwork.logo,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                showPlaceholder = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineSmall,
                color = ink.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PanelButtons(movie: Movie, panelButton: PanelButton) {
    val remaining = remainingMs(movie.resumePositionMs, movie.runtimeMs)
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PanelPill(
            text =
                stringResource(
                    if (remaining != null) R.string.movies_resume else R.string.movies_play,
                ),
            trailing = remaining?.let { remainingText(it) },
            icon = painterResource(R.drawable.ic_play),
            isPrimary = true,
            isSelected = panelButton == PanelButton.Play,
        )
        PanelPill(
            text = stringResource(R.string.movies_details),
            isSelected = panelButton == PanelButton.Details,
        )
    }
}

// Looks like a selected PillButton but has no focus of its own: focus stays on the movie.
@Composable
private fun PanelPill(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    trailing: String? = null,
    isPrimary: Boolean = false,
) {
    val container = if (isPrimary) JellyCastColors.PrimaryContainer else JellyCastColors.Rail
    val content = JellyCastColors.Text
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    val scale = if (isSelected) 1.1f else 1f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .size(PanelButtonWidth, 40.dp)
                .neonFocus(Dimens.PillShape, active = isSelected)
                .background(container, Dimens.PillShape)
                .padding(start = if (icon != null) 16.dp else 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                ),
            color = content,
            maxLines = 1,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PanelMeta(movie: Movie, showProgress: Boolean, ink: SpineInk) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val remaining = remainingMs(movie.resumePositionMs, movie.runtimeMs)
        val showRemaining = showProgress && remaining != null
        if (showRemaining) {
            Text(
                text = stringResource(R.string.runtime_remaining_capital, runtimeText(remaining)),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = JellyCastColors.Accent,
            )
        }
        val parts =
            buildList {
                movie.year?.let { add(it.toString()) }
                movie.runtimeMs?.let { add(runtimeText(it)) }
                if (!showProgress) {
                    movie.genres
                        .take(2)
                        .takeIf { it.isNotEmpty() }
                        ?.let { add(it.map { g -> genreName(g) }.joinToString(", ")) }
                }
                techSummary(movie.tech)?.let { add(it) }
            }
        val separatorAfterRemaining = if (showRemaining) "·  " else ""
        Text(
            text = separatorAfterRemaining + parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun techSummary(tech: MediaTech): String? {
    val parts = listOfNotNull(
        tech.resolution?.let { resolutionLabel(it) },
        tech.dynamicRange?.let { dynamicRangeLabel(it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}
