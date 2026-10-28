package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val FieldShape = RoundedCornerShape(12.dp)
private val CompactShape = RoundedCornerShape(percent = 50)

/**
 * On TV, Compose opens the on-screen keyboard as soon as a text field gets focus, after which
 * up/down go to the keyboard instead of the next control. So the field stays read-only while
 * navigating past it; only OK starts editing and opens the keyboard. Up/down always move focus,
 * left/right too while not editing. The keyboard's action key stops editing and calls
 * [onImeAction].
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: () -> Unit = {},
    compact: Boolean = false,
    placeholder: String? = null,
    leadingIcon: Painter? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme

    val shape = if (compact) CompactShape else FieldShape
    val textStyle =
        if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !isEditing,
            singleLine = true,
            textStyle = textStyle.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions =
                KeyboardActions(
                    onAny = {
                        isEditing = false
                        keyboard?.hide()
                        onImeAction()
                    },
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (!it.isFocused) isEditing = false
                    }.onPreviewKeyEvent { event ->
                        handleFieldKey(event, isEditing, focusManager) {
                            isEditing = true
                            keyboard?.show()
                        }
                    }.background(colors.surface, shape)
                    .border(
                        width = if (isFocused) (if (compact) 2.dp else 3.dp) else 1.dp,
                        color = if (isFocused) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant.copy(
                                alpha = 0.4f,
                            )
                        },
                        shape = shape,
                    ).then(
                        if (compact) {
                            Modifier.height(34.dp).padding(horizontal = 14.dp)
                        } else {
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        },
                    ),
            decorationBox = { innerTextField ->
                FieldDecoration(
                    leadingIcon = leadingIcon,
                    placeholder = placeholder.takeIf { value.isEmpty() },
                    textStyle = textStyle,
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

/** Returns whether the key was consumed. */
private fun handleFieldKey(
    event: KeyEvent,
    isEditing: Boolean,
    focusManager: FocusManager,
    startEditing: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    when (event.key) {
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)

        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)

        // A read-only field still consumes left/right as cursor moves, which would trap focus
        // inside the field.
        Key.DirectionLeft, Key.DirectionRight -> {
            if (isEditing) return false
            focusManager.moveFocus(
                if (event.key == Key.DirectionLeft) FocusDirection.Left else FocusDirection.Right,
            )
        }

        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            if (isEditing) return false
            startEditing()
        }

        else -> return false
    }
    return true
}

@Composable
private fun FieldDecoration(
    leadingIcon: Painter?,
    placeholder: String?,
    textStyle: TextStyle,
    innerTextField: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Box(contentAlignment = Alignment.CenterStart) {
            if (placeholder != null) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            innerTextField()
        }
    }
}
