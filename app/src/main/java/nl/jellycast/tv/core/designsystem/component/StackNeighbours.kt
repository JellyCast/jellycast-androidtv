package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

/** "‹ previous   3 / 12   next ›", for screens where left/right steps through single items. */
@Composable
fun StackNeighbours(
    previous: String?,
    next: String?,
    position: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (previous != null) {
            NeighbourLabel(
                title = previous,
                iconRes = R.drawable.ic_chevron_left,
                color = muted,
                iconFirst = true,
            )
        }
        Text(
            text = stringResource(R.string.stack_position, position, count),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em),
            color = muted.copy(alpha = 0.7f),
        )
        if (next != null) {
            NeighbourLabel(
                title = next,
                iconRes = R.drawable.ic_chevron_right,
                color = MaterialTheme.colorScheme.onSurface,
                iconFirst = false,
            )
        }
    }
}

@Composable
private fun NeighbourLabel(
    title: String,
    iconRes: Int,
    color: Color,
    iconFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = @Composable {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        if (iconFirst) icon()
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = NeighbourMaxWidth),
        )
        if (!iconFirst) icon()
    }
}

private val NeighbourMaxWidth = 160.dp

@Preview(device = Devices.TV_1080p)
@Composable
private fun StackNeighboursPreview() {
    JellyCastTheme {
        StackNeighbours(previous = "Severance", next = "Dune: Part Two", position = 3, count = 5)
    }
}
