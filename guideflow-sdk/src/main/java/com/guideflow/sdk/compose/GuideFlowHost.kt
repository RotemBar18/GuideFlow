package com.guideflow.sdk.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.flow.ActiveFlowState
import com.guideflow.shared.StepType

/**
 * Renders host-app [content] and, on top of it, the active GuideFlow overlay.
 * Place once near the app root.
 */
@Composable
fun GuideFlowHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        val activeFlow by GuideFlow.coordinator.activeFlow.collectAsState()
        activeFlow?.let { state -> GuideFlowOverlay(state) }
    }
}

/**
 * Picks the overlay for the current step. Tooltip and spotlight fall back to a
 * modal (and emit ANCHOR_MISSING) when their anchor isn't on screen.
 */
@Composable
private fun GuideFlowOverlay(state: ActiveFlowState) {
    val step = state.currentStep
    val anchorKey = step.anchorKey
    val anchor = anchorKey?.let { GuideFlow.anchors.resolve(it) }
    val requiresAnchor = step.type == StepType.TOOLTIP || step.type == StepType.SPOTLIGHT
    val anchorMissing = requiresAnchor && anchor == null

    if (anchorMissing && anchorKey != null) {
        LaunchedEffect(state.flow.flowKey, step.id) {
            GuideFlow.coordinator.notifyAnchorMissing(anchorKey)
        }
    }

    // Overlay placement uses absolute root pixels, so it must stay LTR; RTL is
    // applied only to the card's text content (see StepControls). Otherwise
    // Modifier.offset mirrors the X axis and anchors land on the wrong side.
    when {
        step.type == StepType.MODAL || anchorMissing -> ModalFallback(state)
        step.type == StepType.TOOLTIP -> TooltipOverlay(state, anchor!!)
        step.type == StepType.SPOTLIGHT -> SpotlightOverlay(state, anchor!!)
    }
}
