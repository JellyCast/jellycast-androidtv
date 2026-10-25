package nl.jellycast.tv.feature.movies

import androidx.compose.ui.unit.dp
import nl.jellycast.tv.core.designsystem.theme.Dimens

internal object BookcaseDimens {
    val ScreenWidth = 960.dp
    val TopGap = 6.dp
    val Height = 431.dp
    val SpineWidth = 44.dp
    val SpineGap = 4.dp
    val PanelWidth = 500.dp
    val SpinePosterHeight = 78.dp
    val SpineLogoTop = 92.dp
    val SpineLogoWidth = 30.dp

    // Shorter than the design so the logo stays well clear of the tech line.
    val SpineLogoLength = 212.dp

    val PanelOffsetFromMargin = 124.dp

    // Room after the last movie so it can also sit at the fixed panel position.
    val EndPadding = ScreenWidth - Dimens.SafeHorizontal - PanelOffsetFromMargin - PanelWidth
}
