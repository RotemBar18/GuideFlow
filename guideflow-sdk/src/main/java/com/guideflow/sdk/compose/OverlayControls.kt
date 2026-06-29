package com.guideflow.sdk.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.flow.ActiveFlowState

/**
 * Title, body, step progress, and the Skip / Back / Next(Done) controls shared by
 * every overlay type. Buttons drive the [GuideFlow.coordinator] directly.
 */
@Composable
internal fun StepControls(state: ActiveFlowState, modifier: Modifier = Modifier) {
    val coordinator = GuideFlow.coordinator
    val step = state.currentStep
    Column(modifier) {
        Text(step.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(step.body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Step ${state.currentStepIndex + 1} of ${state.totalSteps}",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { coordinator.skip() },
                modifier = Modifier.testTag(GuideFlowOverlayTags.SKIP),
            ) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            if (!state.isFirstStep) {
                TextButton(
                    onClick = { coordinator.back() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.BACK),
                ) { Text("Back") }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = { coordinator.next() },
                modifier = Modifier.testTag(GuideFlowOverlayTags.NEXT),
            ) {
                Text(if (state.isLastStep) "Done" else "Next")
            }
        }
    }
}

/** Swallow taps that fall outside interactive controls so the host UI can't be touched. */
internal fun Modifier.consumeTaps(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }
