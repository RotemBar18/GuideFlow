package com.guideflow.sdk.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.guideflow.sdk.api.GuideFlow

/**
 * Registers the composable as a GuideFlow anchor under [key].
 *
 * Tracks the element's bounds in root coordinates across layout changes and
 * removes the anchor when the composable leaves composition.
 *
 * When the active step targets this anchor and is `advanceOnTap`, a tap on the
 * element advances the flow. The tap is observed in the Initial pass without being
 * consumed, so the element's own click handler still fires (e.g. navigation). The
 * overlay leaves this element uncovered (see HoleScrim) so it actually gets the tap.
 */
fun Modifier.guideFlowAnchor(key: String): Modifier = composed {
    val anchors = GuideFlow.anchors
    DisposableEffect(key) {
        onDispose { anchors.remove(key) }
    }
    val active by GuideFlow.coordinator.activeFlow.collectAsState()
    val advanceHere = active?.currentStep?.let { it.advanceOnTap && it.anchorKey == key } ?: false

    val base = onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            anchors.update(key, coordinates.boundsInRoot())
        }
    }
    if (!advanceHere) {
        base
    } else {
        base.pointerInput(key) {
            awaitPointerEventScope {
                while (true) {
                    // Initial pass: see the gesture before the element's own click consumes it.
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    if (downEvent.changes.any { it.changedToDownIgnoreConsumed() }) {
                        var advanced = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToUpIgnoreConsumed() }) {
                                advanced = true
                                break
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        if (advanced) GuideFlow.coordinator.next()
                    }
                }
            }
        }
    }
}
