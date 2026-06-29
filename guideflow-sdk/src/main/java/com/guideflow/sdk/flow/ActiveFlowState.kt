package com.guideflow.sdk.flow

import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep

/**
 * Runtime status of an active flow.
 *
 * Note: this is the *runtime* status, distinct from `FlowStatus` in the shared
 * module which describes a flow's content lifecycle (DRAFT/PUBLISHED/ARCHIVED).
 */
enum class FlowRuntimeStatus {
    RUNNING,
    COMPLETED,
    SKIPPED,
    STOPPED,
}

/** Immutable snapshot of the currently running flow. */
data class ActiveFlowState(
    val flow: TutorialFlow,
    val currentStepIndex: Int,
    val status: FlowRuntimeStatus = FlowRuntimeStatus.RUNNING,
) {
    val currentStep: TutorialStep get() = flow.steps[currentStepIndex]
    val totalSteps: Int get() = flow.steps.size
    val isFirstStep: Boolean get() = currentStepIndex == 0
    val isLastStep: Boolean get() = currentStepIndex == flow.steps.lastIndex
}
