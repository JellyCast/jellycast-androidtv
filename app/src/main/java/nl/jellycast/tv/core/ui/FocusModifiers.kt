package nl.jellycast.tv.core.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.focusProperties

/**
 * Left on the first item and right on the last item do nothing (no wrap-around). Turns the
 * element into a focus group, because without a group `onExit` has nothing to attach to.
 */
fun Modifier.stopHorizontalExit(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Left ||
            requestedFocusDirection == FocusDirection.Right
        ) {
            cancelFocusChange()
        }
    }
}.focusGroup()

/**
 * A focus request from code (opening a screen, returning from the player) arrives as
 * [FocusDirection.Enter]; an `onEnter` must never redirect that, or focus restoration lands on
 * item 1 instead of the item the user came back to.
 */
val FocusEnterExitScope.isArrowKey: Boolean
    get() =
        requestedFocusDirection == FocusDirection.Up ||
            requestedFocusDirection == FocusDirection.Down ||
            requestedFocusDirection == FocusDirection.Left ||
            requestedFocusDirection == FocusDirection.Right
