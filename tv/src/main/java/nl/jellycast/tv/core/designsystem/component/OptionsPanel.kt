package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.isArrowKey

private val OptionsWidth = 320.dp

@Composable
fun HoldOkHint(modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .height(20.dp)
                    .border(1.dp, muted.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.movies_ok_key),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.em,
                    ),
                color = muted,
            )
        }
        Text(
            text = stringResource(R.string.movies_hold_ok),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

/**
 * The panel that holding OK opens. [content] puts `Modifier.focusRequester(first)` on the row
 * that gets focus when the panel opens. Focus stays inside the panel until Back.
 */
@Composable
fun OptionsPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(first: FocusRequester) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(
        modifier =
            modifier
                .width(OptionsWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .padding(horizontal = 36.dp, vertical = 32.dp)
                .focusProperties { onExit = { if (isArrowKey) cancelFocusChange() } }
                .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.12.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        content(first)
    }
}

@Composable
fun OptionsDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun OptionRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp).neonFocus(shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = JellyCastColors.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = JellyCastColors.Text,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = JellyCastColors.Text,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OptionsPanelPreview() {
    JellyCastTheme {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            OptionsPanel(title = "The Bear") { first ->
                OptionRow(
                    text = "Mark as watched",
                    onClick = {},
                    modifier = Modifier.focusRequester(first),
                )
                OptionsDivider()
                OptionRow(text = "Series page", onClick = {})
            }
        }
    }
}
