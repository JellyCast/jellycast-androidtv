package nl.jellycast.tv.feature.player

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.SegmentType
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.neonFocus
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

private val PillShape = RoundedCornerShape(50)

private val CountdownLineInset = 26.dp
private val CountdownLineBottom = 10.dp

/** One pill for every jump in an episode. [label] and [countdown] are used for the next episode. */
@Composable
fun JumpPill(
    title: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    label: String? = null,
    countdown: (() -> Float)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .neonFocus(PillShape)
                .then(
                    if (focused) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, JellyCastColors.Text.copy(alpha = 0.32f), PillShape)
                    },
                ).background(JellyCastColors.Background.copy(alpha = 0.82f), PillShape)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .then(if (countdown != null) Modifier.countdownLine(countdown) else Modifier)
                .height(if (label != null) 66.dp else 44.dp)
                .padding(
                    start = if (label != null) 26.dp else 22.dp,
                    end = 18.dp,
                    bottom = if (countdown != null) 6.dp else 0.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label != null) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = JellyCastColors.TextMuted,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_player_next),
            contentDescription = null,
            tint = JellyCastColors.Text,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun Modifier.countdownLine(progress: () -> Float): Modifier = drawBehind {
    val inset = CountdownLineInset.toPx()
    val height = 2.dp.toPx()
    val top = size.height - CountdownLineBottom.toPx() - height
    val width = size.width - 2 * inset
    val radius = CornerRadius(height / 2)
    drawRoundRect(
        color = JellyCastColors.Text.copy(alpha = 0.16f),
        topLeft = Offset(inset, top),
        size = Size(width, height),
        cornerRadius = radius,
    )
    val done = width * progress().coerceIn(0f, 1f)
    if (done > 0f) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    JellyCastColors.ProgressStart,
                    JellyCastColors.ProgressMid,
                    JellyCastColors.ProgressEnd,
                ),
                startX = inset,
                endX = inset + width,
            ),
            topLeft = Offset(inset, top),
            size = Size(done, height),
            cornerRadius = radius,
        )
    }
}

@StringRes
fun SegmentType.skipLabel(): Int = when (this) {
    SegmentType.Intro -> R.string.player_skip_intro
    SegmentType.Recap -> R.string.player_skip_recap
    SegmentType.Commercial -> R.string.player_skip_commercial
    SegmentType.Preview -> R.string.player_skip_preview
    SegmentType.Outro -> R.string.player_skip_outro
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun JumpPillPreview() {
    JellyCastTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            JumpPill(
                title = stringResource(SegmentType.Intro.skipLabel()),
                onClick = {},
                focusRequester = remember { FocusRequester() },
            )
            JumpPill(
                title = "S1 · E2  Hard Salt Beef",
                label = stringResource(R.string.player_next_episode),
                onClick = {},
                focusRequester = remember { FocusRequester() },
                countdown = { 0.2f },
            )
        }
    }
}
