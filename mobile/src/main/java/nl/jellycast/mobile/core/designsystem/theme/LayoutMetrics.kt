package nl.jellycast.mobile.core.designsystem.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The iOS `LayoutMetrics`. Regular (tablet) only when both width and height are at least 600 dp,
 * so a phone held sideways stays compact.
 */
data class LayoutMetrics(
    val isRegular: Boolean,
    val margin: Dp,
    val textMaxWidth: Dp,
    val upNextCardWidth: Dp,
    val posterWidth: Dp,
    val castImageSize: Dp,
    val cardSpacing: Dp,
    val heroLogoSize: DpSize,
) {
    companion object {
        val Compact =
            LayoutMetrics(
                isRegular = false,
                margin = 20.dp,
                textMaxWidth = Dp.Infinity,
                upNextCardWidth = 220.dp,
                posterWidth = 120.dp,
                castImageSize = 72.dp,
                cardSpacing = 12.dp,
                heroLogoSize = DpSize(280.dp, 80.dp),
            )
        val Regular =
            LayoutMetrics(
                isRegular = true,
                margin = 32.dp,
                textMaxWidth = 640.dp,
                upNextCardWidth = 300.dp,
                posterWidth = 150.dp,
                castImageSize = 88.dp,
                cardSpacing = 16.dp,
                heroLogoSize = DpSize(420.dp, 120.dp),
            )
    }
}

val LocalLayout = staticCompositionLocalOf { LayoutMetrics.Compact }

private val RegularMin = 600.dp

@Composable
fun ProvideLayoutMetrics(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val regular = maxWidth >= RegularMin && maxHeight >= RegularMin
        CompositionLocalProvider(
            LocalLayout provides if (regular) LayoutMetrics.Regular else LayoutMetrics.Compact,
            content = content,
        )
    }
}

/**
 * The window's height. `LocalWindowInfo.containerSize` came back too small on Android 12 for a
 * screen first composed in landscape (seen on a Tab 16); the configuration is right on every
 * version.
 */
@Composable
fun windowHeight(): Dp = LocalConfiguration.current.screenHeightDp.dp
