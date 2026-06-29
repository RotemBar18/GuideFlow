package com.guideflow.sdk.anchor

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

/**
 * Tracks anchor keys and their on-screen bounds.
 *
 * Backed by Compose snapshot state so reads in [resolve] recompose the overlay
 * when an anchor appears, moves, or is removed. Populated by `Modifier.guideFlowAnchor`.
 */
internal class AnchorManager {

    private val anchors = mutableStateMapOf<String, Rect>()

    /** Insert or update the bounds for [key]. */
    fun update(key: String, bounds: Rect) {
        anchors[key] = bounds
    }

    /** Remove [key] when its composable leaves composition. */
    fun remove(key: String) {
        anchors.remove(key)
    }

    /** Resolve current anchor info, or null if no composable is registered for [key]. */
    fun resolve(key: String): AnchorInfo? {
        val bounds = anchors[key] ?: return null
        return AnchorInfo(key, bounds)
    }
}
