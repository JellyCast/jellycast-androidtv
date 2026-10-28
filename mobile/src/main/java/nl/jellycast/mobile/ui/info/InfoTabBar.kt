package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
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

/** A rail capsule of equal segments; the chosen one is a grey capsule. Chat gets a sparkle. */
@Composable
internal fun InfoTabBar(
    tabs: List<InfoTab>,
    selected: InfoTab,
    onSelect: (InfoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .padding(3.dp)
            .selectableGroup(),
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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab == InfoTab.Chat) {
                    Icon(
                        painterResource(R.drawable.ic_info_sparkle),
                        null,
                        Modifier.padding(end = 5.dp).size(12.dp),
                        tint = JellyCastColors.Accent,
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

private val InfoTab.title: Int
    get() = when (this) {
        InfoTab.Episodes -> R.string.info_tab_episodes
        InfoTab.Cast -> R.string.info_tab_cast
        InfoTab.Related -> R.string.info_tab_related
        InfoTab.Chat -> R.string.info_tab_chat
    }
