package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

private const val PILL_FOCUS_SCALE = 1.1f
private const val TRAILING_ALPHA = 0.75f

/**
 * A disabled button is dimmed like the skeleton button but keeps focus. [trailing] is a second,
 * lighter text such as "18 min left".
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    trailing: String? = null,
) {
    val container =
        when {
            !enabled -> JellyCastColors.AccentDimmed
            isPrimary -> JellyCastColors.PrimaryContainer
            else -> JellyCastColors.Rail
        }
    val content = JellyCastColors.Text
    Surface(
        onClick = { if (enabled) onClick() },
        modifier =
            modifier
                .height(40.dp)
                .neonFocus(Dimens.PillShape, focusScale = PILL_FOCUS_SCALE, halo = true),
        shape = ClickableSurfaceDefaults.shape(Dimens.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = container,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        // neonFocus handles scale and border; the Surface itself does not react to focus.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (isPrimary && enabled) {
                        Border(
                            BorderStroke(1.dp, JellyCastColors.NeonPurple.copy(alpha = 0.45f)),
                            shape = Dimens.PillShape,
                        )
                    } else {
                        Border.None
                    },
                focusedBorder = Border.None,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(start = if (icon != null) 16.dp else 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                    ),
                maxLines = 1,
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Normal,
                    ),
                    color = LocalContentColor.current.copy(alpha = TRAILING_ALPHA),
                    maxLines = 1,
                    // With a fixed button width, a long translation shortens this part, not the
                    // button text itself.
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PillButtonPreview() {
    JellyCastTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton(text = "Play", onClick = {}, isPrimary = true)
            PillButton(text = "Other", onClick = {})
            PillButton(text = "Play", onClick = {}, isPrimary = true, enabled = false)
        }
    }
}
