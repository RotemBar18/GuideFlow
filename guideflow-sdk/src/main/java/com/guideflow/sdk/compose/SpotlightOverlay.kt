package com.guideflow.sdk.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.anchor.AnchorInfo
import com.guideflow.sdk.flow.ActiveFlowState

/**
 * Dims the whole screen and punches a transparent, rounded cutout over the anchor
 * so the highlighted element shows through. The controls card sits opposite the
 * anchor (top when the element is low, bottom otherwise) so it never covers it.
 *
 * The cutout uses [BlendMode.Clear], which requires the canvas to render into an
 * offscreen layer ([CompositingStrategy.Offscreen]).
 */
@Composable
internal fun SpotlightOverlay(state: ActiveFlowState, anchor: AnchorInfo) {
    val theme = state.activeTheme()
    BoxWithConstraints(Modifier.fillMaxSize().testTag(GuideFlowOverlayTags.SPOTLIGHT)) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // Element low on screen -> card at top, so the highlighted element stays visible.
        val cardAtTop = anchor.bounds.center.y > screenHeightPx * 0.55f
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
        val bg = theme.backgroundColorOrNull()
        Card(
            modifier = Modifier
                .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (cardAtTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(theme.cornerRadius.dp),
            colors = if (bg != null) CardDefaults.cardColors(containerColor = bg) else CardDefaults.cardColors(),
        ) {
            StepControls(state, Modifier.padding(16.dp), advanceByTap = state.currentStep.advanceOnTap)
        }
    }
}
