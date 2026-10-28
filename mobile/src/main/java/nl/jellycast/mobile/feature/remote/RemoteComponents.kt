package nl.jellycast.mobile.feature.remote

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassBorder
import nl.jellycast.mobile.core.designsystem.component.GlassFill
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush
import nl.jellycast.mobile.core.remote.RemoteItem
import nl.jellycast.mobile.core.remote.RemoteSession

/** A flat surface with the still scaled to fill; never an icon for a missing image. */
@Composable
internal fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(JellyCastColors.Surface)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The progress gradient on a faint track; [progress] is read while drawing. */
@Composable
internal fun RemoteProgressLine(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(JellyCastColors.Text.copy(alpha = TRACK_ALPHA), cornerRadius = radius)
                drawRoundRect(
                    ProgressBrush,
                    size = Size(size.width * progress().coerceIn(0f, 1f), size.height),
                    cornerRadius = radius,
                )
            },
    )
}

private const val TRACK_ALPHA = 0.22f

/** A round glass button with an icon, as the player uses over the picture. */
@Composable
internal fun RemoteGlassButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(iconSize), JellyCastColors.Text)
    }
}

/** An icon without a surface, for the top bar over the still. */
@Composable
internal fun RemoteIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(44.dp).clip(CircleShape).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(22.dp), JellyCastColors.Text)
    }
}

/** "Skip intro" as a glass capsule. */
@Composable
internal fun SkipSegmentPill(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_remote_skip_next),
            null,
            Modifier.size(14.dp),
            JellyCastColors.Text,
        )
        Box(Modifier.width(6.dp))
        Text(
            title,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
        )
    }
}

internal val PreviewSession =
    RemoteSession(
        id = "preview",
        deviceId = "preview-device",
        deviceName = "Living room",
        item =
            RemoteItem(
                id = "episode",
                name = "Melbourne",
                seriesName = "The Pacific",
                seasonNumber = 1,
                episodeNumber = 3,
                runtimeMs = 56 * 60_000L,
            ),
        positionMs = (23 * 60 + 41) * 1_000L,
        isPaused = false,
        playMethod = PlayMethod.DirectPlay,
        checkedInAtMs = null,
        canControl = true,
        audioStreamIndex = 1,
        subtitleStreamIndex = null,
    )
