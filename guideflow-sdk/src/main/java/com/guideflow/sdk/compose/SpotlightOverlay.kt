package com.guideflow.sdk.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.anchor.AnchorInfo
import com.guideflow.sdk.flow.ActiveFlowState
import kotlin.math.roundToInt

/**
 * Dims the whole screen and punches a transparent, rounded cutout over the anchor
 * so the highlighted element shows through. The controls card floats next to the
 * element (just below it, or above when there isn't room below).
 *
 * The cutout uses [BlendMode.Clear], which requires the canvas to render into an
 * offscreen layer ([CompositingStrategy.Offscreen]).
 */
@Composable
internal fun SpotlightOverlay(state: ActiveFlowState, anchor: AnchorInfo) {
    val theme = state.activeTheme()
    BoxWithConstraints(Modifier.fillMaxSize().testTag(GuideFlowOverlayTags.SPOTLIGHT)) {
        val density = LocalDensity.current
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 16.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = Color.Black.copy(alpha = theme.dimOpacity))
            val padPx = 8.dp.toPx()
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(anchor.bounds.left - padPx, anchor.bounds.top - padPx),
                size = Size(
                    width = anchor.bounds.width + padPx * 2,
                    height = anchor.bounds.height + padPx * 2,
                ),
                cornerRadius = CornerRadius(theme.cornerRadius.dp.toPx()),
                blendMode = BlendMode.Clear,
            )
        }
        // Block taps; on advance-on-tap steps leave the anchor open so it stays tappable.
        if (state.currentStep.advanceOnTap) HoleScrim(anchor.bounds)
        else Box(Modifier.fillMaxSize().consumeTaps())

        // Float the controls card next to the highlighted element: below it if there is
        // room, otherwise above it. Measured so the "above" case sits fully on screen.
        var cardHeightPx by remember { mutableStateOf(0) }
        val belowY = anchor.bounds.bottom + gapPx
        val fitsBelow = belowY + cardHeightPx <= maxHeightPx - marginPx
        val yPx = if (fitsBelow) belowY else (anchor.bounds.top - gapPx - cardHeightPx).coerceAtLeast(marginPx)
        val bg = theme.backgroundColorOrNull()
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, yPx.roundToInt()) }
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .onSizeChanged { cardHeightPx = it.height },
            shape = RoundedCornerShape(theme.cornerRadius.dp),
            colors = if (bg != null) CardDefaults.cardColors(containerColor = bg) else CardDefaults.cardColors(),
        ) {
            StepControls(state, Modifier.padding(16.dp), advanceByTap = state.currentStep.advanceOnTap)
        }
    }
}
