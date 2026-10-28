package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The top of a movie or series detail: the backdrop from the top edge of the screen, fading into
 * the background, with the logo (or the title) centred over its bottom edge. [overlay] sits in
 * the middle of the backdrop, below the status bar (the round play button).
 */
@Composable
fun DetailHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val layout = LocalLayout.current
    val logoSize = layout.heroLogoSize
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val windowHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val heroHeight = min(maxWidth / HERO_ASPECT + statusBar, windowHeight * MAX_WINDOW_FRACTION)
        Column {
            Box(Modifier.fillMaxWidth().height(heroHeight).background(JellyCastColors.Surface)) {
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxSize().background(HeroScrim))
                Box(
                    Modifier.fillMaxSize().padding(top = statusBar),
                    contentAlignment = Alignment.Center,
                    content = overlay,
                )
            }
            Spacer(Modifier.height(logoSize.height / 2))
        }
        ItemLogo(
            url = logoUrl,
            title = title,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = layout.margin)
                    .sizeIn(maxWidth = logoSize.width, maxHeight = logoSize.height),
        )
    }
}

/** The logo fitted in its frame; the title when there is none or it fails to load. */
@Composable
private fun ItemLogo(url: String?, title: String, modifier: Modifier = Modifier) {
    if (url == null) {
        LogoTitle(title, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        error = { LogoTitle(title) },
    )
}

@Composable
private fun LogoTitle(title: String, modifier: Modifier = Modifier) {
    BasicText(
        text = title,
        modifier = modifier,
        style =
            TextStyle(
                color = JellyCastColors.Text,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 34.sp),
    )
}

/**
 * The round play button over a hero's backdrop with, while in progress, the progress under it.
 * [caption] says what it plays, such as "Continue · S2 · E3".
 */
@Composable
fun HeroPlayButton(
    contentDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    caption: String? = null,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, JellyCastColors.Text.copy(alpha = 0.85f), CircleShape)
                .pressable(onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription,
                Modifier.offset(x = 28.dp * PLAY_ICON_NUDGE).size(28.dp),
                tint = JellyCastColors.Text,
            )
        }
        if (progress != null) HeroProgress(progress)
        if (caption != null) {
            Text(
                caption,
                style =
                    JellyCastText.Footnote.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset.Zero, 8f),
                    ),
                color = JellyCastColors.Text,
            )
        }
    }
}

@Composable
private fun HeroProgress(progress: Float) {
    Box(
        Modifier.width(96.dp).height(3.dp).drawBehind {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(JellyCastColors.Text.copy(alpha = 0.3f), cornerRadius = radius)
            drawRoundRect(
                ProgressBrush,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = radius,
            )
        },
    )
}

private val HeroScrim =
    Brush.verticalGradient(
        0f to JellyCastColors.Background.copy(alpha = 0.45f),
        0.3f to Color.Transparent,
        0.5f to Color.Transparent,
        0.8f to JellyCastColors.Background.copy(alpha = 0.75f),
        1f to JellyCastColors.Background,
    )

private const val HERO_ASPECT = 16f / 9f
private const val MAX_WINDOW_FRACTION = 0.55f
private const val PLAY_ICON_NUDGE = 0.08f
