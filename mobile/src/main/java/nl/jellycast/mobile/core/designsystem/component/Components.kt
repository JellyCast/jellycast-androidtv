package nl.jellycast.mobile.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush

private const val PRESSED_ALPHA = 0.7f

/** iOS `PressedOpacityButtonStyle`: every tappable card dims to 70% while pressed. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .alpha(if (pressed) PRESSED_ALPHA else 1f)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/** Capsule in accent, at least 52 high, full width: the primary action of a page. */
@Composable
fun AccentButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Accent)
            .pressable(onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(15.dp), tint = JellyCastColors.OnAccent)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = JellyCastText.Headline.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.OnAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                " $detail",
                style = JellyCastText.Body,
                color = JellyCastColors.OnAccent.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** iOS glass: a translucent light fill with a thin light border. */
val GlassFill = Color.White.copy(alpha = 0.12f)
val GlassBorder = Color.White.copy(alpha = 0.16f)

@Composable
fun GlassCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription,
            Modifier.size(17.dp),
            tint = JellyCastColors.Text,
        )
    }
}

/** A glass capsule button with text, e.g. "Try again". */
@Composable
fun GlassButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
        )
    }
}

@Composable
fun RailCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = JellyCastColors.Text,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(18.dp), tint = tint)
    }
}

/** Title (title3 bold), muted subtitle, and an optional trailing link, on one line. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    linkTitle: String? = null,
    onLink: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = JellyCastText.Title3.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, bottom = 1.dp).weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        if (linkTitle != null && onLink != null) {
            Text(
                linkTitle,
                style = JellyCastText.Subheadline,
                color = JellyCastColors.Accent,
                modifier = Modifier.pressable(onLink).padding(bottom = 1.dp),
            )
        }
    }
}

/** Footnote semibold, uppercase, tracking 0.8, muted. */
@Composable
fun ListSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = JellyCastText.Footnote.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = JellyCastColors.TextMuted,
        modifier = modifier,
    )
}

/** Capsule track with the progress gradient. [onImage]: the 3 dp variant over a picture. */
@Composable
fun WatchProgressBar(progress: Float, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val track = if (onImage) {
        JellyCastColors.Background.copy(
            alpha = 0.7f,
        )
    } else {
        JellyCastColors.Text.copy(alpha = 0.22f)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (onImage) 3.dp else 4.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                drawRoundRect(
                    ProgressBrush,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                    cornerRadius = radius,
                )
            },
    )
}

/** Caption2 bold uppercase on a dark capsule, e.g. "NEW". */
@Composable
fun ImageBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = JellyCastText.Caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = JellyCastColors.Text,
        modifier =
            modifier
                .clip(CircleShape)
                .background(JellyCastColors.Background.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Message plus a glass "Try again", centred. */
@Composable
fun ContentErrorView(
    message: String,
    retryTitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
        GlassButton(retryTitle, onRetry)
    }
}

private val SheetRowShape = RoundedCornerShape(18.dp)

/** A row in a bottom sheet: min 56 high, faint fill and border, optional icon circle. */
@Composable
fun SheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = JellyCastColors.Text,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(SheetRowShape)
            .background(JellyCastColors.Text.copy(alpha = 0.05f))
            .border(BorderStroke(1.5.dp, JellyCastColors.Text.copy(alpha = 0.10f)), SheetRowShape)
            .pressable(onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(18.dp), tint = iconTint)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun CheckedSheetRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    SheetRow(title, onClick, modifier, detail) {
        if (selected) {
            Icon(
                painterResource(nl.jellycast.mobile.R.drawable.ic_check),
                null,
                Modifier.size(16.dp),
                tint = JellyCastColors.Accent,
            )
        }
    }
}

@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = JellyCastText.Title2.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        if (subtitle != null) {
            Text(subtitle, style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
        }
    }
}

/** A flat surface block, the skeleton and image placeholder. */
@Composable
fun SurfaceBlock(modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    Surface(modifier, shape = RoundedCornerShape(radius), color = JellyCastColors.Surface) {}
}
