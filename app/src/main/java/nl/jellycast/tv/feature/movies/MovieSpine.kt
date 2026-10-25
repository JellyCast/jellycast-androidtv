package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.AgeRating
import nl.jellycast.tv.core.model.Artwork
import nl.jellycast.tv.core.model.DynamicRange
import nl.jellycast.tv.core.model.MediaTech
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.Resolution
import nl.jellycast.tv.core.model.Spine
import nl.jellycast.tv.core.model.progress
import nl.jellycast.tv.core.ui.dynamicRangeShortLabel
import nl.jellycast.tv.core.ui.resolutionLabel

private val SpineEdgeDark = Color(0x59000000)
private val SpineEdgeLight = Color(0x1AFFFFFF)

private val SpineProgressBottomUp =
    Brush.verticalGradient(
        0f to JellyCastColors.ProgressEnd,
        0.3f to JellyCastColors.ProgressMid,
        1f to JellyCastColors.ProgressStart,
    )

internal data class SpineInk(val text: Color, val muted: Color, val line: Color)

internal fun Spine.ink(): SpineInk = if (isLight) {
    SpineInk(
        text = JellyCastColors.Background,
        muted = JellyCastColors.Background.copy(alpha = 0.7f),
        line = JellyCastColors.Background.copy(alpha = 0.18f),
    )
} else {
    SpineInk(
        text = JellyCastColors.Text,
        muted = JellyCastColors.Text.copy(alpha = 0.7f),
        line = JellyCastColors.Text.copy(alpha = 0.18f),
    )
}

@Composable
internal fun MovieSpine(movie: Movie, showProgress: Boolean, modifier: Modifier = Modifier) {
    val spine = movie.spine
    val ink = spine.ink()
    Box(
        modifier
            .size(BookcaseDimens.SpineWidth, BookcaseDimens.Height)
            // Rendered once to its own texture: scrolling the bookcase then draws one texture
            // per spine instead of about ten separate draw operations.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(Color(spine.color)),
    ) {
        Column {
            ArtworkImage(
                url = spine.posterUrl,
                showPlaceholder = false,
                modifier = Modifier.fillMaxWidth().height(BookcaseDimens.SpinePosterHeight),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line))
        }
        SpineLogo(
            movie = movie,
            ink = ink,
            modifier =
                Modifier.align(Alignment.TopCenter).padding(top = BookcaseDimens.SpineLogoTop),
        )
        SpineTech(
            movie = movie,
            ink = ink,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(1.dp)
                .fillMaxHeight()
                .background(SpineEdgeDark),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(SpineEdgeLight),
        )
        val progress = movie.progress
        if (showProgress && progress != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .width(3.dp)
                    .fillMaxHeight(progress)
                    .background(SpineProgressBottomUp),
            )
        }
    }
}

@Composable
private fun SpineLogo(movie: Movie, ink: SpineInk, modifier: Modifier = Modifier) {
    Box(
        modifier.size(BookcaseDimens.SpineLogoWidth, BookcaseDimens.SpineLogoLength),
        contentAlignment = Alignment.Center,
    ) {
        val readsBottomToTop =
            Modifier
                .requiredSize(BookcaseDimens.SpineLogoLength, BookcaseDimens.SpineLogoWidth)
                .rotate(-90f)
        if (movie.spine.logoUrl != null) {
            ArtworkImage(
                url = movie.spine.logoUrl,
                contentScale = ContentScale.Fit,
                showPlaceholder = false,
                modifier = readsBottomToTop,
            )
        } else {
            Box(readsBottomToTop, contentAlignment = Alignment.Center) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ink.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SpineTech(movie: Movie, ink: SpineInk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        movie.tech.resolution?.let {
            Text(
                text = resolutionLabel(it),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = ink.text,
            )
        }
        val rangeOrAtmos =
            movie.tech.dynamicRange?.let { dynamicRangeShortLabel(it) }
                ?: if (movie.tech.hasAtmos) {
                    stringResource(R.string.tech_atmos).uppercase()
                } else {
                    null
                }
        if (rangeOrAtmos != null) {
            Text(
                text = rangeOrAtmos,
                style = MaterialTheme.typography.labelSmall,
                color = ink.muted,
            )
        }
        movie.ageRating?.let { rating ->
            Box(Modifier.width(20.dp).height(1.dp).background(ink.line))
            Box(
                Modifier.size(24.dp).border(1.5.dp, ink.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rating.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.text,
                    maxLines = 1,
                )
            }
        }
    }
}

internal val PreviewMovie =
    Movie(
        id = "m1",
        title = "Interstellar",
        year = 2014,
        runtimeMs = 10_144_000,
        overview = "The adventures of a group of explorers who make use of a newly discovered " +
            "wormhole to surpass the limitations on human space travel.",
        genres = listOf("Adventure", "Science Fiction"),
        ageRating = AgeRating("12"),
        resumePositionMs = 4_000_000,
        lastPlayedAt = null,
        addedAt = null,
        artwork = Artwork(),
        spine = Spine(posterUrl = null, logoUrl = null, color = 0xFF20282BL, isLight = false),
        tech = MediaTech(Resolution.UHD, DynamicRange.DolbyVision, hasAtmos = true),
    )

@Preview(device = Devices.TV_1080p)
@Composable
private fun MovieSpinePreview() {
    JellyCastTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(BookcaseDimens.SpineGap)) {
            MoviePanel(movie = PreviewMovie, panelButton = PanelButton.Play, showProgress = true)
            MovieSpine(movie = PreviewMovie, showProgress = true)
            MovieSpine(
                movie = PreviewMovie.copy(
                    spine = PreviewMovie.spine.copy(color = 0xFFD8CFC4, isLight = true),
                ),
                showProgress = false,
            )
        }
    }
}
