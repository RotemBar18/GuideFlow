package com.guideflow.sdk.flow

import com.guideflow.sdk.api.GuideFlowListener
import com.guideflow.sdk.api.GuideFlowLog
import com.guideflow.sdk.api.StopReason
import com.guideflow.shared.EventType
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single active flow and its current step. Pure Kotlin (no Compose) so
 * the navigation logic is unit-testable. Exposes [activeFlow] as a [StateFlow] the
 * Compose host observes; null means no flow is running.
 *
 * [recordEvent] receives analytics events (type, flowId, stepId); it defaults to a
 * no-op so unit tests can construct the coordinator with just a listener.
 */
internal class FlowCoordinator(
    private val listenerProvider: () -> GuideFlowListener?,
    private val recordEvent: (EventType, String, String?) -> Unit = { _, _, _ -> },
) {
    private val _activeFlow = MutableStateFlow<ActiveFlowState?>(null)
    val activeFlow: StateFlow<ActiveFlowState?> = _activeFlow.asStateFlow()

    private val listener: GuideFlowListener? get() = listenerProvider()

    fun start(flow: TutorialFlow): Result<Unit> {
        if (_activeFlow.value != null) {
            return Result.failure(IllegalStateException("A GuideFlow tutorial is already active"))
        }
        val ordered = flow.copy(steps = flow.steps.sortedBy { it.order })
        _activeFlow.value = ActiveFlowState(ordered, currentStepIndex = 0)
        listener?.onFlowStarted(ordered.flowKey)
        listener?.onStepChanged(ordered.flowKey, 0)
        recordEvent(EventType.FLOW_STARTED, ordered.id, null)
        recordEvent(EventType.STEP_SHOWN, ordered.id, ordered.steps.firstOrNull()?.id)
        return Result.success(Unit)
    }

    fun next() {
        val state = _activeFlow.value ?: return
        recordEvent(EventType.STEP_COMPLETED, state.flow.id, state.currentStep.id)
        if (state.isLastStep) {
            complete()
            return
        }
        val index = state.currentStepIndex + 1
        _activeFlow.value = state.copy(currentStepIndex = index)
        listener?.onStepChanged(state.flow.flowKey, index)
        recordEvent(EventType.STEP_SHOWN, state.flow.id, state.flow.steps[index].id)
    }

    fun back() {
        val state = _activeFlow.value ?: return
        if (state.isFirstStep) return
        val index = state.currentStepIndex - 1
        _activeFlow.value = state.copy(currentStepIndex = index)
        listener?.onStepChanged(state.flow.flowKey, index)
        recordEvent(EventType.STEP_SHOWN, state.flow.id, state.flow.steps[index].id)
    }

    fun skip() {
        val state = _activeFlow.value ?: return
        _activeFlow.value = null
        listener?.onFlowSkipped(state.flow.flowKey)
        recordEvent(EventType.FLOW_SKIPPED, state.flow.id, state.currentStep.id)
    }

    /** Notify the listener that the current step's anchor could not be found. */
    fun notifyAnchorMissing(anchorKey: String) {
        val state = _activeFlow.value ?: return
        GuideFlowLog.w(
            "anchor \"$anchorKey\" not on screen for flow \"${state.flow.flowKey}\" (step ${state.currentStepIndex + 1}); " +
                "showing modal fallback. Add Modifier.guideFlowAnchor(\"$anchorKey\") to the target composable.",
        )
        listener?.onAnchorMissing(state.flow.flowKey, anchorKey)
        recordEvent(EventType.ANCHOR_MISSING, state.flow.id, state.currentStep.id)
    }

    fun stop(reason: StopReason) {
        if (_activeFlow.value == null) return
        when (reason) {
            StopReason.COMPLETED -> complete()
            StopReason.SKIPPED -> skip()
            StopReason.MANUAL -> _activeFlow.value = null
        }
    }

    private fun complete() {
        val state = _activeFlow.value ?: return
        _activeFlow.value = null
        listener?.onFlowCompleted(state.flow.flowKey)
        recordEvent(EventType.FLOW_COMPLETED, state.flow.id, null)
    }
}
