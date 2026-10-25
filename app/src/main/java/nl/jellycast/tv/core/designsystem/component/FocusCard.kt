package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.MaterialTheme
import nl.jellycast.tv.core.designsystem.theme.Dimens

/**
 * Deliberately not a tv-material `Surface`: its composition cost per card is too high when a
 * whole row of cards appears per key press. Here a focus change only touches the draw phase.
 * The clip comes after the neon border so the border may fall outside the card.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    shape: Shape = Dimens.CardShape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentOnFocus by rememberUpdatedState(onFocus)
    Box(
        modifier =
            modifier
                .onFocusChanged { state -> if (state.isFocused) currentOnFocus() }
                .neonFocus(shape, focusScale = Dimens.FOCUS_SCALE, halo = true)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                }.then(
                    if (onLongClick == null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        // Holding OK fires onLongClick; a short OK still clicks on release.
                        Modifier.combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    },
                ).drawWithContent {
                    if (containerColor != Color.Transparent) {
                        drawOutline(
                            shape.createOutline(size, layoutDirection, this),
                            containerColor,
                        )
                    }
                    drawContent()
                },
        content = content,
    )
}
