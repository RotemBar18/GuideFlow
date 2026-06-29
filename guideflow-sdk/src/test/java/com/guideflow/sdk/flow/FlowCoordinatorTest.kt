package com.guideflow.sdk.flow

import com.guideflow.sdk.api.GuideFlowListener
import com.guideflow.sdk.api.StopReason
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowCoordinatorTest {

    private class RecordingListener : GuideFlowListener {
        val events = mutableListOf<String>()
        override fun onFlowStarted(flowKey: String) { events += "started:$flowKey" }
        override fun onStepChanged(flowKey: String, stepIndex: Int) { events += "step:$stepIndex" }
        override fun onFlowCompleted(flowKey: String) { events += "completed:$flowKey" }
        override fun onFlowSkipped(flowKey: String) { events += "skipped:$flowKey" }
    }

    private fun step(order: Int) = TutorialStep(
        id = "s$order",
        order = order,
        type = StepType.MODAL,
        anchorKey = null,
        title = "title $order",
        body = "body $order",
    )

    private fun flow(vararg orders: Int) = TutorialFlow(
        id = "flow",
        flowKey = "tour",
        name = "Tour",
        status = FlowStatus.PUBLISHED,
        steps = orders.map { step(it) },
    )

    @Test
    fun start_setsFirstStepAndNotifies() {
        val listener = RecordingListener()
        val coordinator = FlowCoordinator(listenerProvider = { listener })

        val result = coordinator.start(flow(1, 2, 3))

        assertTrue(result.isSuccess)
        assertEquals(0, coordinator.activeFlow.value?.currentStepIndex)
        assertEquals(listOf("started:tour", "step:0"), listener.events)
    }

    @Test
    fun start_sortsStepsByOrder() {
        val coordinator = FlowCoordinator(listenerProvider = { null })

        coordinator.start(flow(3, 1, 2))

        assertEquals(listOf(1, 2, 3), coordinator.activeFlow.value?.flow?.steps?.map { it.order })
    }

    @Test
    fun next_advancesStep() {
        val coordinator = FlowCoordinator(listenerProvider = { null })
        coordinator.start(flow(1, 2, 3))

        coordinator.next()

        assertEquals(1, coordinator.activeFlow.value?.currentStepIndex)
    }

    @Test
    fun next_onLastStep_completesAndClears() {
        val listener = RecordingListener()
        val coordinator = FlowCoordinator(listenerProvider = { listener })
        coordinator.start(flow(1, 2))

        coordinator.next() // -> last step
        coordinator.next() // -> complete

        assertNull(coordinator.activeFlow.value)
        assertTrue(listener.events.contains("completed:tour"))
    }

    @Test
    fun back_goesToPreviousStep() {
        val coordinator = FlowCoordinator(listenerProvider = { null })
        coordinator.start(flow(1, 2, 3))
        coordinator.next()

        coordinator.back()

        assertEquals(0, coordinator.activeFlow.value?.currentStepIndex)
    }

    @Test
    fun back_onFirstStep_isNoOp() {
        val coordinator = FlowCoordinator(listenerProvider = { null })
        coordinator.start(flow(1, 2))

        coordinator.back()

        assertEquals(0, coordinator.activeFlow.value?.currentStepIndex)
    }

    @Test
    fun skip_clearsFlowAndNotifies() {
        val listener = RecordingListener()
        val coordinator = FlowCoordinator(listenerProvider = { listener })
        coordinator.start(flow(1, 2))

        coordinator.skip()

        assertNull(coordinator.activeFlow.value)
        assertTrue(listener.events.contains("skipped:tour"))
    }

    @Test
    fun start_whenAlreadyActive_fails() {
        val coordinator = FlowCoordinator(listenerProvider = { null })
        coordinator.start(flow(1))

        val second = coordinator.start(flow(1))

        assertTrue(second.isFailure)
    }

    @Test
    fun stopManual_clearsWithoutLifecycleCallback() {
        val listener = RecordingListener()
        val coordinator = FlowCoordinator(listenerProvider = { listener })
        coordinator.start(flow(1, 2))

        coordinator.stop(StopReason.MANUAL)

        assertNull(coordinator.activeFlow.value)
        assertTrue(listener.events.none { it.startsWith("completed") || it.startsWith("skipped") })
    }
}
