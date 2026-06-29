package com.guideflow.sdk.flow

import com.guideflow.sdk.api.GuideFlowListener
import com.guideflow.sdk.api.StopReason
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single active flow and its current step. Pure Kotlin (no Compose) so
 * the navigation logic is unit-testable. Exposes [activeFlow] as a [StateFlow] the
 * Compose host observes; null means no flow is running.
 *
 * Listener callbacks are routed through [listenerProvider] so the listener can be
 * swapped at runtime without rebuilding the coordinator.
 */
internal class FlowCoordinator(
    private val listenerProvider: () -> GuideFlowListener?,
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
        return Result.success(Unit)
    }

    fun next() {
        val state = _activeFlow.value ?: return
        if (state.isLastStep) {
            complete()
            return
        }
        val index = state.currentStepIndex + 1
        _activeFlow.value = state.copy(currentStepIndex = index)
        listener?.onStepChanged(state.flow.flowKey, index)
    }

    fun back() {
        val state = _activeFlow.value ?: return
        if (state.isFirstStep) return
        val index = state.currentStepIndex - 1
        _activeFlow.value = state.copy(currentStepIndex = index)
        listener?.onStepChanged(state.flow.flowKey, index)
    }

    fun skip() {
        val state = _activeFlow.value ?: return
        _activeFlow.value = null
        listener?.onFlowSkipped(state.flow.flowKey)
    }

    /** Notify the listener that the current step's anchor could not be found. */
    fun notifyAnchorMissing(anchorKey: String) {
        val flowKey = _activeFlow.value?.flow?.flowKey ?: return
        listener?.onAnchorMissing(flowKey, anchorKey)
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
    }
}
