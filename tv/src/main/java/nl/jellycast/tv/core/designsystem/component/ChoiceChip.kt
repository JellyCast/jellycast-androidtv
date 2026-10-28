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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

private const val CHIP_FOCUS_SCALE = 1.1f

@Composable
fun ChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isSelected) JellyCastColors.Text else Color.Transparent
    val content = if (isSelected) JellyCastColors.Background else JellyCastColors.TextMuted
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp).neonFocus(Dimens.PillShape, CHIP_FOCUS_SCALE),
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    Border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent else JellyCastColors.Rail,
                        ),
                        shape = Dimens.PillShape,
                    ),
                focusedBorder = Border.None,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(start = if (isSelected) 10.dp else 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ChoiceChipPreview() {
    JellyCastTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(label = "Movie", isSelected = false, onClick = {})
            ChoiceChip(label = "Series", isSelected = true, onClick = {})
        }
    }
}
