package nl.jellycast.mobile.feature.series

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.ui.info.SeriesCast

internal enum class SeriesTab(@StringRes val title: Int) {
    Episodes(R.string.series_tab_episodes),
    Cast(R.string.series_tab_cast),
    Chat(R.string.series_tab_chat),
}

private val TabBarHeight = 40.dp
private val TabBarInset = 3.dp

/** A rail capsule with the selected tab filled, like the segmented pickers elsewhere in the app. */
@Composable
internal fun SeriesTabBar(
    tabs: List<SeriesTab>,
    selected: SeriesTab,
    onSelect: (SeriesTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(TabBarHeight)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .padding(TabBarInset),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        if (isSelected) JellyCastColors.TabSelected else JellyCastColors.Rail,
                    )
                    .pressable({ onSelect(tab) })
                    .semantics {
                        this.selected = isSelected
                        role = Role.Tab
                    },
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab == SeriesTab.Chat) {
                    Icon(
                        painterResource(R.drawable.ic_series_sparkle),
                        contentDescription = null,
                        tint = JellyCastColors.Accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    stringResource(tab.title),
                    style =
                        JellyCastText.Subheadline.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = JellyCastColors.Text,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Integration point for the series' own cast and makers: the info worker's cast tab goes here,
 * called with the series id.
 */
@Composable
internal fun SeriesCastTab(seriesId: String, modifier: Modifier = Modifier) {
    SeriesCast(seriesId, modifier)
}
