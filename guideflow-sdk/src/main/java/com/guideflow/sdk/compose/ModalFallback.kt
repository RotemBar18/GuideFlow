package com.guideflow.sdk.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.flow.ActiveFlowState

/**
 * A centered, dimmed modal. Used for MODAL steps and as the fallback when a
 * tooltip/spotlight anchor cannot be found on screen.
 */
@Composable
internal fun ModalFallback(state: ActiveFlowState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .consumeTaps()
            .testTag(GuideFlowOverlayTags.MODAL),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp).widthIn(max = 360.dp)) {
            StepControls(state, Modifier.padding(20.dp))
        }
    }
}
