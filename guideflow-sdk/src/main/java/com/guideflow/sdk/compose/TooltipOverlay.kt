package com.guideflow.sdk.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.anchor.AnchorInfo
import com.guideflow.sdk.flow.ActiveFlowState
import kotlin.math.roundToInt

/**
 * A floating card placed just below the anchor (clamped to stay on screen).
 * Unlike spotlight/modal it does not dim the screen.
 */
@Composable
internal fun TooltipOverlay(state: ActiveFlowState, anchor: AnchorInfo) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val yPx = (anchor.bounds.bottom + 16f)
            .coerceAtMost(maxHeightPx - 240f)
            .coerceAtLeast(0f)
        val bg = state.flow.theme.backgroundColorOrNull()
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, yPx.roundToInt()) }
                .padding(horizontal = 16.dp)
                .widthIn(max = 360.dp)
                .testTag(GuideFlowOverlayTags.TOOLTIP),
            shape = RoundedCornerShape(state.flow.theme.cornerRadius.dp),
            colors = if (bg != null) CardDefaults.cardColors(containerColor = bg) else CardDefaults.cardColors(),
        ) {
            StepControls(state, Modifier.padding(16.dp))
        }
    }
}
