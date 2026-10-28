package nl.jellycast.mobile.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The iOS app's `JellyCastColors`; dark only, the same tokens as the TV app. */
object JellyCastColors {
    val Background = Color(0xFF0E0E0F)
    val Surface = Color(0xFF1A1A1C)
    val Rail = Color(0xFF2A2A2E)
    val Text = Color(0xFFECE9E4)
    val TextMuted = Color(0xFFA3A09A)
    val Accent = Color(0xFFB9A6F2)
    val OnAccent = Color(0xFF1A1206)
    val PrimaryContainer = Color(0xFF16161B)
    val NeonBlue = Color(0xFF7FC8FF)
    val NeonWhite = Color(0xFFF2F6FF)
    val NeonPurple = Color(0xFF9B6BFF)
    val NeonPink = Color(0xFFFF6EC7)
    val ProgressStart = Color(0xFF8FB8D8)
    val ProgressMid = Color(0xFF9A88D0)
    val ProgressEnd = Color(0xFFC584B0)
    val TabSelected = Color(0xFF595959)
    val Warning = Color(0xFFFF9F0A)
}

val ProgressBrush =
    Brush.horizontalGradient(
        listOf(
            JellyCastColors.ProgressStart,
            JellyCastColors.ProgressMid,
            JellyCastColors.ProgressEnd,
        ),
    )

val ProgressTrack = JellyCastColors.Rail

/** The iOS text styles at the default Dynamic Type size, in sp. */
object JellyCastText {
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
    val Title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
    val Title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp)
    val Headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val Subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val Footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
}

private val JellyCastColorScheme =
    darkColorScheme(
        primary = JellyCastColors.Accent,
        onPrimary = JellyCastColors.OnAccent,
        primaryContainer = JellyCastColors.PrimaryContainer,
        onPrimaryContainer = JellyCastColors.Text,
        secondary = JellyCastColors.Accent,
        onSecondary = JellyCastColors.OnAccent,
        secondaryContainer = JellyCastColors.Rail,
        onSecondaryContainer = JellyCastColors.Text,
        background = JellyCastColors.Background,
        onBackground = JellyCastColors.Text,
        surface = JellyCastColors.Background,
        onSurface = JellyCastColors.Text,
        surfaceVariant = JellyCastColors.Surface,
        onSurfaceVariant = JellyCastColors.TextMuted,
        surfaceContainerLowest = JellyCastColors.Background,
        surfaceContainerLow = JellyCastColors.Surface,
        surfaceContainer = JellyCastColors.Surface,
        surfaceContainerHigh = JellyCastColors.Surface,
        surfaceContainerHighest = JellyCastColors.Rail,
        outline = JellyCastColors.Rail,
        outlineVariant = JellyCastColors.Rail,
    )

private val JellyCastTypography =
    Typography(
        displaySmall = JellyCastText.LargeTitle,
        headlineSmall = JellyCastText.Title2,
        titleLarge = JellyCastText.Title3,
        titleMedium = JellyCastText.Headline,
        bodyLarge = JellyCastText.Body,
        bodyMedium = JellyCastText.Subheadline,
        bodySmall = JellyCastText.Footnote,
        labelLarge = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = JellyCastText.Caption,
        labelSmall = JellyCastText.Caption2,
    )

@Composable
fun JellyCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JellyCastColorScheme,
        typography = JellyCastTypography,
        content = content,
    )
}
