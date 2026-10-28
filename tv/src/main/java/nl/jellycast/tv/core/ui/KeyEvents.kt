package nl.jellycast.tv.core.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

val KeyEvent.isOk: Boolean
    get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
