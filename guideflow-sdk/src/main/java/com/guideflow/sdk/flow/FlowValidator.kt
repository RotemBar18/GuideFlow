package com.guideflow.sdk.flow

import com.guideflow.sdk.api.GuideFlowError
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialFlow

/**
 * Validates a flow before it starts. Mirrors the publish-time checks the backend
 * will enforce later: a flow needs at least one step, and tooltip/spotlight steps
 * require an anchor key. Returns null when valid.
 */
internal object FlowValidator {

    fun validate(flow: TutorialFlow): GuideFlowError? {
        if (flow.steps.isEmpty()) {
            return GuideFlowError.InvalidConfig("Flow '${flow.flowKey}' has no steps")
        }
        flow.steps.forEach { step ->
            val needsAnchor = step.type == StepType.TOOLTIP || step.type == StepType.SPOTLIGHT
            if (needsAnchor && step.anchorKey.isNullOrBlank()) {
                return GuideFlowError.InvalidConfig(
                    "Step '${step.id}' of type ${step.type} requires an anchorKey",
                )
            }
        }
        return null
    }
}
