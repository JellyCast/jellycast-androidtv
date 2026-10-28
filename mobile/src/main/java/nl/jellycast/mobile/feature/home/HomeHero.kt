package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.LANDSCAPE_RATIO
import nl.jellycast.mobile.core.designsystem.component.WatchProgressBar
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout

private const val HERO_MAX_WINDOW_FRACTION = 0.55f
private const val FRAME_WIDTH_FRACTION = 0.56f
private const val FRAME_WIDTH_FRACTION_REGULAR = 0.44f
private const val FRAME_HEIGHT_FRACTION = 0.62f
private val ContinueShape = RoundedCornerShape(10.dp)
private val PlayCircleSize = 56.dp
private val LogoTitleSize = 34.sp
private const val LOGO_TITLE_MIN_SCALE = 0.6f

private val HeroGradient =
    arrayOf(
        0f to JellyCastColors.Background.copy(alpha = 0.45f),
        0.3f to Color.Transparent,
        0.5f to Color.Transparent,
        0.8f to JellyCastColors.Background.copy(alpha = 0.75f),
        1f to JellyCastColors.Background,
    )

/** The hero: backdrop under the status bar with the play card, logo, label and detail line. */
@Composable
internal fun HomeHeroCard(
    hero: HomeHero,
    unavailable: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .unavailableWhen(unavailable)
            .pressable(onOpen, enabled = !unavailable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailHero(hero.backdropUrl, hero.logoUrl, hero.title) {
            HeroContinueFrame(hero.item, !unavailable, onPlay, onLongPress)
        }
        Text(
            stringResource(hero.label.textRes).uppercase(),
            style = JellyCastText.Caption.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            ),
            color = JellyCastColors.Accent,
        )
        Text(
            heroDetail(hero.item),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LocalLayout.current.margin),
        )
    }
}

private val HeroLabel.textRes: Int
    get() = when (this) {
        HeroLabel.ContinueWatching -> R.string.home_label_continue
        HeroLabel.NextEpisode -> R.string.home_label_next
        HeroLabel.StartSomethingNew -> R.string.home_label_start_new
    }

@Composable
private fun heroDetail(item: PlayableItem): String = when (item) {
    is PlayableItem.MovieItem -> joined(item.movie.year?.toString(), timeLabel(item))

    is PlayableItem.EpisodeItem ->
        joined(seasonEpisodeText(item.episode), item.episode.title, timeLabel(item))
}

/**
 * Full width, 16:9 below the status bar but never taller than part of the window, fading into
 * the background, with the logo centred over its bottom edge.
 */
@Composable
private fun DetailHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val layout = LocalLayout.current
    val logoSize = layout.heroLogoSize
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = logoSize.height / 2)) {
        val height = min(
            maxWidth / LANDSCAPE_RATIO + topInset,
            windowHeight * HERO_MAX_WINDOW_FRACTION,
        )
        Box(Modifier.fillMaxWidth().height(height).background(JellyCastColors.Surface)) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brush = Brush.verticalGradient(colorStops = HeroGradient)
                        onDrawBehind { drawRect(brush) }
                    },
            )
            Box(Modifier.fillMaxSize().padding(top = topInset), content = overlay)
            HeroLogo(
                logoUrl,
                title,
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = logoSize.height / 2)
                    .padding(horizontal = layout.margin)
                    .widthIn(max = logoSize.width)
                    .fillMaxWidth()
                    .height(logoSize.height),
            )
        }
    }
}

@Composable
private fun HeroLogo(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url == null) {
            LogoTitle(title)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = { LogoTitle(title) },
            )
        }
    }
}

@Composable
private fun LogoTitle(title: String) {
    Text(
        title,
        style = JellyCastText.LargeTitle.copy(fontWeight = FontWeight.ExtraBold),
        color = JellyCastColors.Text,
        textAlign = TextAlign.Center,
        maxLines = 2,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = LogoTitleSize * LOGO_TITLE_MIN_SCALE,
                maxFontSize = LogoTitleSize,
            ),
    )
}

/** The 16:9 still of what continues, centred on the backdrop, with a play button on it. */
@Composable
private fun HeroContinueFrame(
    item: PlayableItem,
    enabled: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regular = LocalLayout.current.isRegular
    val playLabel =
        stringResource(if (item.isInProgress) R.string.home_continue else R.string.home_play)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fraction = if (regular) FRAME_WIDTH_FRACTION_REGULAR else FRAME_WIDTH_FRACTION
        val width: Dp =
            min(maxWidth * fraction, maxHeight * FRAME_HEIGHT_FRACTION * LANDSCAPE_RATIO)
        Box(
            Modifier
                .width(width)
                .shadow(
                    12.dp,
                    ContinueShape,
                    ambientColor = Color.Black.copy(alpha = 0.55f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                )
                .pressable(onPlay, onLongPress, enabled = enabled)
                .semantics {
                    contentDescription = playLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            ContinueImage(item, Modifier.fillMaxWidth())
            Box(
                Modifier.matchParentSize().border(
                    1.dp,
                    JellyCastColors.Text.copy(alpha = 0.14f),
                    ContinueShape,
                ),
            )
            PlayCircle()
        }
    }
}

@Composable
private fun PlayCircle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(PlayCircleSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, JellyCastColors.Text.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = JellyCastColors.Text,
            modifier = Modifier.offset(x = 2.dp).size(26.dp),
        )
    }
}

/** The still of what continues with its progress along the bottom; the hero and Up next share it. */
@Composable
internal fun ContinueImage(item: PlayableItem, modifier: Modifier = Modifier) {
    val progress =
        when (item) {
            is PlayableItem.MovieItem -> item.movie.progress
            is PlayableItem.EpisodeItem -> item.episode.progress
        }
    Box(
        modifier
            .aspectRatio(LANDSCAPE_RATIO)
            .clip(ContinueShape)
            .background(JellyCastColors.Surface),
    ) {
        AsyncImage(
            model = item.continueImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (progress != null && item.isInProgress) {
            WatchProgressBar(
                progress,
                Modifier.align(
                    Alignment.BottomCenter,
                ).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                onImage = true,
            )
        }
    }
}
