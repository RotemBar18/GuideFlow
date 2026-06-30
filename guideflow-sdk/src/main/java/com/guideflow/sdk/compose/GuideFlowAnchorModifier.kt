package com.guideflow.sdk.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.guideflow.sdk.api.GuideFlow
import kotlinx.coroutines.delay

/**
 * Registers the composable as a GuideFlow anchor under [key].
 *
 * Tracks the element's bounds in root coordinates, removes the anchor when the
 * composable leaves composition, and:
 *  - when the active step targets this anchor, asks the nearest scrollable parent to
 *    scroll the element into view, so a step can point at a control below the fold;
 *  - when that step is `advanceOnTap`, observes a tap (Initial pass, non-consuming) and
 *    advances the flow while the element's own onClick still runs.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.guideFlowAnchor(key: String): Modifier = composed {
    val anchors = GuideFlow.anchors
    DisposableEffect(key) {
        onDispose { anchors.remove(key) }
    }
    val active by GuideFlow.coordinator.activeFlow.collectAsState()
    val step = active?.currentStep
    val isTarget = step?.anchorKey == key
    val advanceHere = isTarget && step?.advanceOnTap == true

    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isTarget) {
        if (isTarget) {
            delay(120) // let the (possibly just-navigated) screen lay out first
            runCatching { requester.bringIntoView() }
        }
    }

    val base = Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) anchors.update(key, coordinates.boundsInRoot())
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
