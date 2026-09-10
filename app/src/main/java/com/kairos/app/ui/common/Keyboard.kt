package com.kairos.app.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Close the soft keyboard on any tap that isn't on a text field — the same
 * behaviour the task screen uses, applied app-wide by wrapping the top-level
 * content. The down is observed on the Initial pass and NOT consumed, so the
 * gesture still reaches buttons, scrollers, and text fields (a tapped field
 * re-requests focus after this clears it, so tapping a field still focuses it).
 */
fun Modifier.dismissKeyboardOnTapOutside(focus: FocusManager): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            focus.clearFocus()
        }
    }
