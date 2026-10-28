package nl.jellycast.mobile.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// The TV app's tokens (design-style.md), so both apps look like one product.
private val Background = Color(0xFF0E0E0F)
private val Surface = Color(0xFF1A1A1C)
private val Rail = Color(0xFF2A2A2E)
private val Text = Color(0xFFECE9E4)
private val TextMuted = Color(0xFFA3A09A)
private val Accent = Color(0xFFB9A6F2)
private val PrimaryContainer = Color(0xFF2E2840)

val ProgressBrush =
    Brush.horizontalGradient(
        listOf(Color(0xFF8FB8D8), Color(0xB39A88D0), Color(0xFFC584B0)),
    )

val ProgressTrack = Rail

private val JellyCastColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = Background,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = Text,
        secondary = Accent,
        onSecondary = Background,
        secondaryContainer = PrimaryContainer,
        onSecondaryContainer = Text,
        background = Background,
        onBackground = Text,
        surface = Background,
        onSurface = Text,
        surfaceVariant = Surface,
        onSurfaceVariant = TextMuted,
        surfaceContainerLowest = Background,
        surfaceContainerLow = Surface,
        surfaceContainer = Surface,
        surfaceContainerHigh = Rail,
        surfaceContainerHighest = Rail,
        outline = Rail,
        outlineVariant = Rail,
    )

@Composable
fun JellyCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JellyCastColors, content = content)
}
