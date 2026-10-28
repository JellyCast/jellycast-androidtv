package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush

private const val HERO_ASPECT_RATIO = 16f / 9f
private const val HERO_MAX_WINDOW_FRACTION = 0.55f
private const val PLAY_ICON_NUDGE = 0.08f
private val PlayButtonSize = 76.dp
private val PlayIconSize = 28.dp
private val HeroProgressWidth = 96.dp

private val HeroGradient =
    Brush.verticalGradient(
        0f to JellyCastColors.Background.copy(alpha = 0.45f),
        0.3f to JellyCastColors.Background.copy(alpha = 0f),
        0.5f to JellyCastColors.Background.copy(alpha = 0f),
        0.8f to JellyCastColors.Background.copy(alpha = 0.75f),
        1f to JellyCastColors.Background,
    )

/**
 * The backdrop from the top edge of the screen, fading into the background, with the round play
 * button in the middle and the logo centred over its bottom edge.
 */
@Composable
internal fun SeriesHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    caption: String,
    progress: Float?,
    isPlayable: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val windowHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val logoSize = LocalLayout.current.heroLogoSize
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val heroHeight = min(
            maxWidth / HERO_ASPECT_RATIO + topInset,
            windowHeight * HERO_MAX_WINDOW_FRACTION,
        )
        Box(Modifier.fillMaxWidth().height(heroHeight + logoSize.height / 2)) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxSize().background(HeroGradient))
                HeroPlayButton(
                    caption = caption,
                    progress = progress,
                    isPlayable = isPlayable,
                    onPlay = onPlay,
                    modifier = Modifier.align(Alignment.Center).padding(top = topInset),
                )
            }
            HeroLogo(
                url = logoUrl,
                title = title,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = LocalLayout.current.margin)
                        .size(logoSize),
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            title,
            style = JellyCastText.LargeTitle.copy(fontWeight = FontWeight.ExtraBold),
            color = JellyCastColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 34.sp),
        )
    }
}

@Composable
private fun HeroPlayButton(
    caption: String,
    progress: Float?,
    isPlayable: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadowBlur = with(LocalDensity.current) { 4.dp.toPx() }
    Column(
        modifier.alpha(if (isPlayable) 1f else UNAVAILABLE_ALPHA),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(PlayButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, JellyCastColors.Text.copy(alpha = 0.85f), CircleShape)
                .pressable(onPlay, enabled = isPlayable),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.series_play_accessibility),
                tint = JellyCastColors.Text,
                modifier = Modifier.offset(x = PlayIconSize * PLAY_ICON_NUDGE).size(PlayIconSize),
            )
        }
        if (progress != null) HeroProgressBar(progress, Modifier.width(HeroProgressWidth))
        Text(
            caption,
            style =
                JellyCastText.Footnote.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = shadowBlur),
                ),
            color = JellyCastColors.Text,
        )
    }
}

@Composable
private fun HeroProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val track = JellyCastColors.Text.copy(alpha = 0.3f)
    Box(
        modifier
            .height(height)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                drawRoundRect(
                    ProgressBrush,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                    cornerRadius = radius,
                )
            },
    )
}

/** iOS dims what can't play offline to 35%. */
internal const val UNAVAILABLE_ALPHA = 0.35f
