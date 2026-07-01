package com.guideflow.sdk.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.flow.ActiveFlowState
import com.guideflow.shared.progressText
import kotlin.math.roundToInt

/**
 * Title, body, step progress, and the Skip / Back / Next(Done) controls shared by
 * every overlay type. Buttons drive the [GuideFlow.coordinator] directly.
 */
@Composable
internal fun StepControls(state: ActiveFlowState, modifier: Modifier = Modifier, advanceByTap: Boolean = false) {
    val coordinator = GuideFlow.coordinator
    val step = state.currentStep
    val theme = state.activeTheme()
    // Apply RTL to the text content only; overlay placement stays LTR (see GuideFlowHost).
    val dir = if (state.flow.theme.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Match the portal preview: title full-strength, body/skip/back muted, progress fainter.
    val content = theme.contentColorOrDefault()
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                fontSize = theme.titleSize.sp,
                modifier = Modifier.weight(1f),
            )
            if (theme.showSkip && !state.isLastStep) {
                TextButton(
                    onClick = { coordinator.skip() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.SKIP),
                ) { Text(theme.skipLabel, color = content.copy(alpha = 0.7f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.7f), fontSize = theme.bodySize.sp)
        if (theme.showProgress) {
            Spacer(Modifier.height(10.dp))
            Text(
                theme.progressText(state.currentStepIndex + 1, state.totalSteps),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (theme.showBack && !state.isFirstStep) {
                TextButton(
                    onClick = { coordinator.back() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.BACK),
                ) { Text(theme.backLabel, color = content.copy(alpha = 0.7f)) }
            }
            Spacer(Modifier.weight(1f))
            // Advance-on-tap steps have no Next button; the user taps the element instead.
            if (!advanceByTap) {
                Button(
                    onClick = { coordinator.next() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.NEXT),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accentColorOrDefault(),
                        contentColor = theme.buttonTextColorOrDefault(),
                    ),
                ) {
                    Text(if (state.isLastStep) theme.doneLabel else theme.nextLabel)
                }
            }
        }
    }
    }
}

/**
 * Block taps so the host UI can't be touched during a step. The overlay's own
 * controls (Back/Skip/Next) are children, so they handle taps first (Main pass
 * bubbles child→parent) and keep working. When [hole] is set (advance-on-tap),
 * taps inside that rect are left unconsumed and [onHoleTap] fires.
 *
 * Uses a custom node that opts into [PointerInputModifierNode.sharePointerInputWithSiblings]
 * so the host element under the hole actually receives the tap — a plain
 * full-screen pointerInput would occlude it (scrims don't share with siblings).
 */
internal fun Modifier.consumeTaps(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            // requireUnconsumed = true so the overlay's own controls (children) win first.
            val down = awaitFirstDown(requireUnconsumed = true)
            down.consume()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * Blocks taps everywhere except inside [hole] (advance-on-tap steps), leaving that
 * rectangle uncovered so the host element underneath actually receives the tap —
 * its onClick runs and `Modifier.guideFlowAnchor` advances the flow. Built from four
 * strips around the hole because a full-screen pointer scrim occludes the host element.
 */
@Composable
internal fun HoleScrim(hole: Rect) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val top = hole.top.coerceIn(0f, hPx)
        val bottom = hole.bottom.coerceIn(top, hPx)
        val left = hole.left.coerceIn(0f, wPx)
        val right = hole.right.coerceIn(left, wPx)
        Strip(0f, 0f, wPx, top)                  // above the hole
        Strip(0f, bottom, wPx, hPx - bottom)     // below
        Strip(0f, top, left, bottom - top)       // left
        Strip(right, top, wPx - right, bottom - top) // right
    }
}

@Composable
private fun Strip(xPx: Float, yPx: Float, wPx: Float, hPx: Float) {
    if (wPx <= 0f || hPx <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .consumeTaps(),
    )
}
