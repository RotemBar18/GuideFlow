package com.guideflow.sdk.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
 * element advances the flow. The tap is observed without being consumed, so the
 * host's own click handler still fires (e.g. navigation to the next screen).
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
    if (!advanceHere) base
    else base.pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // Non-consuming: the host element still receives the tap.
            if (waitForUpOrCancellation() != null) GuideFlow.coordinator.next()
        }
    }
}
