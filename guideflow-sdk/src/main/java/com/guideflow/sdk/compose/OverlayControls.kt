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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val theme = state.activeTheme()
    val textColor = theme.textColorOrNull() ?: Color.Unspecified
    Column(modifier) {
        Text(step.title, style = MaterialTheme.typography.titleMedium, color = textColor)
        Spacer(Modifier.height(6.dp))
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = textColor)
        if (theme.showProgress) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Step ${state.currentStepIndex + 1} of ${state.totalSteps}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (theme.showSkip) {
                TextButton(
                    onClick = { coordinator.skip() },
                    modifier = Modifier.testTag(GuideFlowOverlayTags.SKIP),
                ) { Text(theme.skipLabel) }
            }
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentColorOrDefault()),
            ) {
                Text(if (state.isLastStep) theme.doneLabel else theme.nextLabel)
            }
        }
    }
}

/** Swallow taps that fall outside interactive controls so the host UI can't be touched. */
internal fun Modifier.consumeTaps(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }
