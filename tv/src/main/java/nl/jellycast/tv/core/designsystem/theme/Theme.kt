package nl.jellycast.tv.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

object JellyCastColors {
    val Background = Color(0xFF0E0E0F)
    val Surface = Color(0xFF1A1A1C)
    val Rail = Color(0xFF2A2A2E)
    val Text = Color(0xFFECE9E4)
    val TextMuted = Color(0xFFA3A09A)

    /** Focus only (neon border, weld spark); never used as a fill. */
    val NeonBlue = Color(0xFF7FC8FF)
    val NeonWhite = Color(0xFFF2F6FF)
    val NeonPurple = Color(0xFF9B6BFF)
    val NeonPink = Color(0xFFFF6EC7)

    val ProgressStart = Color(0xFF8FB8D8)
    val ProgressMid = Color(0xFF9A88D0)
    val ProgressEnd = Color(0xFFC584B0)

    val Accent = Color(0xFFB9A6F2)
    val PrimaryContainer = Color(0xFF16161B)
    val Online = Color(0xFF7FB77E)
    val AccentDimmed = Color(0xFF2E2840)
}

private val ColorScheme =
    darkColorScheme(
        primary = JellyCastColors.Accent,
        onPrimary = JellyCastColors.Background,
        background = JellyCastColors.Background,
        onBackground = JellyCastColors.Text,
        surface = JellyCastColors.Surface,
        onSurface = JellyCastColors.Text,
        surfaceVariant = JellyCastColors.Rail,
        onSurfaceVariant = JellyCastColors.TextMuted,
        border = JellyCastColors.NeonPurple,
    )

private val JellyCastTypography =
    Typography(
        displaySmall = TextStyle(
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineMedium =
            TextStyle(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.01).em,
            ),
        titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Light),
        headlineSmall = TextStyle(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
        titleSmall = TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelMedium = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelSmall =
            TextStyle(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
    )

@Composable
fun JellyCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = JellyCastTypography,
        content = content,
    )
}
