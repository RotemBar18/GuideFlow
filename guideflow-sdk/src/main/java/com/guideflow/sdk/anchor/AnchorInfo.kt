package com.guideflow.sdk.anchor

import androidx.compose.ui.geometry.Rect

/** A resolved anchor: its stable key and current bounds in root coordinates. */
data class AnchorInfo(
    val key: String,
    val bounds: Rect,
)
