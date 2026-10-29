package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.jellycast.core.network.withFillWidth
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.LANDSCAPE_RATIO
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush

private val BadgeBackground = Color(0xFF0E0E0F)
private val ProgressTrackOnImage = Color.White.copy(alpha = 0.2f)

/** Sizes of one tile; phone and tablet, small and featured, differ only in these. */
internal data class TileStyle(
    val imageRadius: Dp,
    val progressHeight: Dp,
    val badgeInset: Dp,
    val timeBadgeSize: TextUnit,
    val timeBadgeRadius: Dp,
    val title: TextStyle,
    val meta: TextStyle,
    val textTop: Dp,
    val isRegular: Boolean,
)

internal fun tileStyle(isRegular: Boolean, featured: Boolean): TileStyle {
    val phoneSmall = !isRegular && !featured
    val titleSize =
        when {
            featured && isRegular -> 22.sp
            featured -> 20.sp
            phoneSmall -> 15.sp
            else -> 16.sp
        }
    val metaSize =
        when {
            featured && isRegular -> 16.sp
            featured -> 15.sp
            phoneSmall -> 13.sp
            else -> 14.sp
        }
    return TileStyle(
        imageRadius = if (featured) 12.dp else 10.dp,
        progressHeight =
            when {
                featured -> 5.dp
                phoneSmall -> 3.dp
                else -> 4.dp
            },
        badgeInset = if (phoneSmall) 8.dp else 10.dp,
        timeBadgeSize = if (phoneSmall) 12.sp else 13.sp,
        timeBadgeRadius = if (phoneSmall) 5.dp else 6.dp,
        title =
            TextStyle(
                fontSize = titleSize,
                lineHeight = titleSize * LINE_HEIGHT_FACTOR,
                fontWeight = if (featured) FontWeight.Bold else FontWeight.SemiBold,
            ),
        meta = TextStyle(fontSize = metaSize, lineHeight = metaSize * LINE_HEIGHT_FACTOR),
        textTop = if (featured) 10.dp else 8.dp,
        isRegular = isRegular,
    )
}

private const val LINE_HEIGHT_FACTOR = 1.3f
private val TitleMetaGap = 2.dp

/** What a tile takes under its image; the title and meta lines have fixed heights. */
@Composable
internal fun TileStyle.textBlockHeight(): Dp = with(LocalDensity.current) {
    textTop + title.lineHeight.toDp() + TitleMetaGap + meta.lineHeight.toDp()
}

/**
 * A 16:9 image with its badges and progress, then one line of title and one of meta, so every
 * tile in a row has the same height. [imageHeight] replaces the 16:9 image height; with
 * [letterboxed] the image keeps 16:9 inside that frame, on black.
 */
@Composable
internal fun HomeTileCard(
    tile: HomeTile,
    style: TileStyle,
    imageWidthPx: Int,
    unavailable: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp? = null,
    letterboxed: Boolean = false,
) {
    val density = LocalDensity.current
    Column(
        modifier
            .unavailableWhen(unavailable)
            .pressable(onClick, onLongPress, enabled = !unavailable),
    ) {
        TileImage(
            tile,
            style,
            imageWidthPx,
            letterboxed,
            if (imageHeight != null) {
                Modifier.fillMaxWidth().height(imageHeight)
            } else {
                Modifier.fillMaxWidth().aspectRatio(LANDSCAPE_RATIO)
            },
        )
        Spacer(Modifier.height(style.textTop))
        Text(
            tile.title,
            style = style.title,
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(with(density) { style.title.lineHeight.toDp() }),
        )
        Spacer(Modifier.height(TitleMetaGap))
        Text(
            tile.meta(),
            style = style.meta,
            color = JellyCastColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(with(density) { style.meta.lineHeight.toDp() }),
        )
    }
}

@Composable
private fun TileImage(
    tile: HomeTile,
    style: TileStyle,
    imageWidthPx: Int,
    letterboxed: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(style.imageRadius)
    val frameColor = if (letterboxed) Color.Black else JellyCastColors.Surface
    Box(modifier.clip(shape).background(frameColor)) {
        AsyncImage(
            model = tile.imageUrl?.let {
                if (tile is HomeTile.Ad) it else withFillWidth(it, imageWidthPx)
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                if (letterboxed) {
                    Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(LANDSCAPE_RATIO)
                } else {
                    Modifier.fillMaxSize()
                },
        )
        tile.label?.let { label ->
            TileLabel(
                label,
                style.isRegular,
                Modifier.align(Alignment.TopStart).padding(style.badgeInset),
            )
        }
        tile.timeBadge(style.isRegular)?.let { text ->
            TimeBadge(text, style, Modifier.align(Alignment.BottomEnd).padding(style.badgeInset))
        }
        tile.progress?.let { progress ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(style.progressHeight)
                    .drawBehind {
                        drawRect(ProgressTrackOnImage)
                        drawRect(
                            ProgressBrush,
                            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                        )
                    },
            )
        }
    }
}

@Composable
private fun TimeBadge(text: String, style: TileStyle, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = style.timeBadgeSize, fontWeight = FontWeight.SemiBold),
        color = JellyCastColors.Text,
        maxLines = 1,
        modifier =
            modifier
                .clip(RoundedCornerShape(style.timeBadgeRadius))
                .background(BadgeBackground.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal enum class TileLabel { New, NotStarted, Ad }

private val HomeTile.label: TileLabel?
    get() = when (this) {
        is HomeTile.Continue -> null
        is HomeTile.New -> TileLabel.New
        is HomeTile.NotStarted -> TileLabel.NotStarted
        is HomeTile.Ad -> TileLabel.Ad
    }

@Composable
private fun TileLabel(label: TileLabel, isRegular: Boolean, modifier: Modifier = Modifier) {
    val text =
        stringResource(
            when (label) {
                TileLabel.New -> R.string.home_badge_new
                TileLabel.NotStarted -> R.string.home_badge_not_started
                TileLabel.Ad -> R.string.ad_label
            },
        )
    val isNew = label == TileLabel.New
    if (isRegular) {
        val shape = RoundedCornerShape(6.dp)
        LabelText(
            text,
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
            if (isNew) JellyCastColors.OnAccent else JellyCastColors.Text,
            modifier
                .height(22.dp)
                .clip(shape)
                .then(
                    if (isNew) {
                        Modifier.background(JellyCastColors.Accent)
                    } else {
                        Modifier
                            .background(JellyCastColors.Background.copy(alpha = 0.8f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
                    },
                ).padding(horizontal = 8.dp),
        )
    } else {
        LabelText(
            text,
            TextStyle(
                fontSize = 10.sp,
                fontWeight = if (isNew) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.05.em,
            ),
            if (isNew) JellyCastColors.Accent else JellyCastColors.Text.copy(alpha = 0.8f),
            modifier
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BadgeBackground.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun LabelText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = style, color = color, maxLines = 1)
    }
}
