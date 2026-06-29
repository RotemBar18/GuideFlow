package com.guideflow.sdk.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.guideflow.sdk.api.GuideFlow

/**
 * Registers the composable as a GuideFlow anchor under [key].
 *
 * Tracks the element's bounds in root coordinates across layout changes and
 * removes the anchor when the composable leaves composition.
 */
fun Modifier.guideFlowAnchor(key: String): Modifier = composed {
    val anchors = GuideFlow.anchors
    DisposableEffect(key) {
        onDispose { anchors.remove(key) }
    }
    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            anchors.update(key, coordinates.boundsInRoot())
        }
    }
}
