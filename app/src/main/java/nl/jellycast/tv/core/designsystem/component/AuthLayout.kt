package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

val AuthContentWidth = 360.dp

private val BrandStart = 96.dp
private val ContentStart = 520.dp

/**
 * The glow behind the brand is one pre-rendered image, not gradients drawn every frame.
 *
 * [footerReserve] keeps room at the bottom for a tall [footer]; the brand is centred in the space
 * above it, so it does not jump when the footer text changes.
 */
@Composable
fun AuthLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 18.dp,
    footerReserve: Dp = 0.dp,
    footer: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = JellyCastColors.Background),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.auth_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = footerReserve),
            ) {
                Brand(modifier = Modifier.align(Alignment.CenterStart).padding(start = BrandStart))
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = ContentStart)
                        .width(AuthContentWidth),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Dimens.SafeHorizontal, bottom = Dimens.SafeVertical),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(Modifier.size(148.dp))
        BrandWordmark()
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AuthHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.12.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** [minLines] keeps the height fixed when the text changes, so the centred column does not jump. */
@Composable
fun AuthText(text: String, modifier: Modifier = Modifier, minLines: Int = 1) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = minLines,
        modifier = modifier.width(AuthContentWidth),
    )
}

@Composable
fun AuthError(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.width(AuthContentWidth),
    )
}

/** "—— or ——" between two ways to sign in. */
@Composable
fun AuthDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.width(AuthContentWidth).padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Line()
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Line()
    }
}

@Composable
private fun RowScope.Line() {
    Spacer(
        modifier =
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(JellyCastColors.Rail),
    )
}

@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 250.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(Dimens.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = JellyCastColors.Rail,
                contentColor = JellyCastColors.Text,
                focusedContainerColor = JellyCastColors.Text,
                focusedContentColor = JellyCastColors.Background,
                pressedContainerColor = JellyCastColors.Text,
                pressedContentColor = JellyCastColors.Background,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Dimens.FOCUS_SCALE),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun AuthLayoutPreview() {
    JellyCastTheme {
        AuthLayout {
            AuthHeading("Sign in with password")
            AuthText("Enter your Jellyfin user name and password.")
            AuthDivider()
            AuthButton(
                text = "Sign in with code",
                onClick = {},
                icon = painterResource(R.drawable.ic_phone),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
