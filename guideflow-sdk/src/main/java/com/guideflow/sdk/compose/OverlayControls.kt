package com.guideflow.sdk.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.flow.ActiveFlowState
import com.guideflow.shared.progressText

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
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (theme.showSkip && !state.isLastStep) {
                TextButton(
                    onClick = { coordinator.skip() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.SKIP),
                ) { Text(theme.skipLabel) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(step.body, style = MaterialTheme.typography.bodyMedium)
        if (theme.showProgress) {
            Spacer(Modifier.height(10.dp))
            Text(
                theme.progressText(state.currentStepIndex + 1, state.totalSteps),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.isFirstStep) {
                TextButton(
                    onClick = { coordinator.back() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.BACK),
                ) { Text(theme.backLabel) }
            }
            Spacer(Modifier.weight(1f))
            if (advanceByTap) {
                // No Next button: the user advances by tapping the highlighted element.
                Text("👆", style = MaterialTheme.typography.titleMedium)
            } else {
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
 * Swallow taps so the host UI can't be touched during a step. When [hole] is set
 * (advance-on-tap steps), taps inside that rect pass through to the host element.
 */
internal fun Modifier.consumeTaps(hole: Rect? = null): Modifier =
    pointerInput(hole) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val passThrough = hole != null && event.changes.any { hole.contains(it.position) }
                if (!passThrough) event.changes.forEach { it.consume() }
            }
        }
    }
