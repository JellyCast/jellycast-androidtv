package nl.jellycast.tv.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Which setting has focus; its explanation is shown in the footer. */
internal class ExplanationState<T : Enum<T>>(private val none: T) {
    var explained: T by mutableStateOf(none)
        private set

    fun onFocusChanged(what: T, isFocused: Boolean) {
        if (isFocused) {
            explained = what
        } else if (explained == what) {
            explained = none
        }
    }
}

internal fun <T : Enum<T>> Modifier.explains(state: ExplanationState<T>, what: T): Modifier =
    onFocusChanged { state.onFocusChanged(what, it.isFocused) }
