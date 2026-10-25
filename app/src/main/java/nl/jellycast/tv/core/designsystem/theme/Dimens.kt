package nl.jellycast.tv.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Dimens {
    /** Overscan margin: text and controls stay inside it, backdrops run to the screen edge. */
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp

    /** Keeps a scaled-up focused element from touching its neighbours. */
    val ItemGap = 16.dp

    val CardGap = 20.dp
    val RowGap = 28.dp

    val PosterWidth = 120.dp
    val PosterHeight = 180.dp

    val LandscapeWidth = 196.dp
    val LandscapeHeight = 110.dp

    val SectionBarHeight = 76.dp

    const val FOCUS_SCALE = 1.05f

    val CardShape = RoundedCornerShape(8.dp)
    val PillShape = RoundedCornerShape(percent = 50)
}
