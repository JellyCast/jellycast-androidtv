package nl.jellycast.tv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.neonFocus
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.model.HomeSection
import nl.jellycast.tv.core.ui.isArrowKey

@Composable
internal fun SectionBar(
    selected: HomeSection,
    tabRequesters: Map<HomeSection, FocusRequester>,
    settingsRequester: FocusRequester,
    onSelectSection: (HomeSection) -> Unit,
    onOpenSettings: () -> Unit,
    onTabsFocus: () -> Unit,
    onSettingsFocus: () -> Unit,
    accessory: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                // Own layer, so scrolling content underneath does not redraw the bar.
                .graphicsLayer()
                .fillMaxWidth()
                .height(Dimens.SectionBarHeight)
                .padding(top = 8.dp)
                .padding(start = Dimens.SafeHorizontal - TabPadding, end = Dimens.SafeHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .onFocusChanged { if (it.hasFocus) onTabsFocus() }
                    // Entering from below must land on the selected tab: focusing the nearest
                    // tab would switch sections.
                    .focusProperties {
                        onEnter =
                            { if (isArrowKey) tabRequesters.getValue(selected).requestFocus() }
                    }
                    .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(28.dp - TabPadding * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeSection.entries.forEach { section ->
                SectionTab(
                    label = stringResource(section.labelRes()),
                    isSelected = section == selected,
                    onFocus = { onSelectSection(section) },
                    modifier = Modifier.focusRequester(tabRequesters.getValue(section)),
                )
            }
        }
        accessory()
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onOpenSettings,
            modifier =
                Modifier
                    .focusRequester(settingsRequester)
                    .neonFocus(Dimens.PillShape, focusScale = 1.1f)
                    .onFocusChanged { if (it.isFocused) onSettingsFocus() },
            shape = ClickableSurfaceDefaults.shape(Dimens.PillShape),
            colors = transparentSurfaceColors(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.home_settings),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
    }
}

private val TabPadding = 10.dp

@Composable
private fun SectionTab(
    label: String,
    isSelected: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onFocus,
        modifier =
            modifier.onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = transparentSurfaceColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = TabPadding, vertical = 6.dp)
                    .width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            val selectedColor = MaterialTheme.colorScheme.onSurface
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawWithCache {
                        val neon = Brush.horizontalGradient(TabNeon)
                        val radius = CornerRadius(size.height / 2)
                        onDrawBehind {
                            when {
                                focused -> {
                                    for ((growDp, alpha) in TabGlowGrowDpToAlpha) {
                                        val extra = growDp.dp.toPx()
                                        drawRoundRect(
                                            brush = neon,
                                            topLeft = Offset(-extra / 2, -extra / 2),
                                            size =
                                                Size(size.width + extra, size.height + extra),
                                            cornerRadius = CornerRadius(size.height / 2 + extra),
                                            alpha = alpha,
                                        )
                                    }
                                    drawRoundRect(brush = neon, cornerRadius = radius)
                                }

                                isSelected -> drawRoundRect(selectedColor, cornerRadius = radius)
                            }
                        }
                    },
            )
        }
    }
}

@Composable
internal fun transparentSurfaceColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    pressedContainerColor = Color.Transparent,
)

private val TabNeon =
    listOf(JellyCastColors.NeonBlue, JellyCastColors.NeonWhite, JellyCastColors.NeonPurple)
private val TabGlowGrowDpToAlpha = listOf(8f to 0.07f, 4f to 0.16f)

private fun HomeSection.labelRes(): Int = when (this) {
    HomeSection.Home -> R.string.home_section_home
    HomeSection.Series -> R.string.home_section_series
    HomeSection.Movies -> R.string.home_section_movies
    HomeSection.Library -> R.string.home_section_library
}
