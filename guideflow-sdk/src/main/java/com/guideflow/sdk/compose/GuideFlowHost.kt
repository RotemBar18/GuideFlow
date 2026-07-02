package com.guideflow.sdk.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
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

    // Grace period: on a cross-screen step, the target composable can register its
    // anchor a few frames after the step becomes active. Wait briefly before treating
    // the anchor as missing, so we neither flash a modal nor log a false ANCHOR_MISSING.
    var graceElapsed by remember(state.flow.flowKey, step.id) { mutableStateOf(false) }
    if (requiresAnchor && anchor == null) {
        LaunchedEffect(state.flow.flowKey, step.id) {
            delay(ANCHOR_GRACE_MS)
            graceElapsed = true
        }
    }
    val anchorMissing = requiresAnchor && anchor == null && graceElapsed

    if (anchorMissing && anchorKey != null) {
        LaunchedEffect(state.flow.flowKey, step.id) {
            GuideFlow.coordinator.notifyAnchorMissing(anchorKey)
        }
    }

    // Overlay placement uses absolute root pixels, so it must stay LTR; RTL is
    // applied only to the card's text content (see StepControls). Otherwise
    // Modifier.offset mirrors the X axis and anchors land on the wrong side.
    when {
        step.type == StepType.MODAL -> ModalFallback(state)
        anchorMissing -> ModalFallback(state)
        anchor != null && step.type == StepType.TOOLTIP -> TooltipOverlay(state, anchor)
        anchor != null && step.type == StepType.SPOTLIGHT -> SpotlightOverlay(state, anchor)
        // else: within the grace window, anchor not yet resolved — render nothing briefly.
    }
}

/** How long to wait for a step's anchor to register before falling back to a modal. */
private const val ANCHOR_GRACE_MS = 350L
