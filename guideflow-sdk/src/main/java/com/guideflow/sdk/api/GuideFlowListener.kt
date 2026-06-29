package com.guideflow.sdk.api

/**
 * Optional host-app callbacks for tutorial lifecycle events. Every method has a
 * default no-op body so hosts override only what they need.
 */
interface GuideFlowListener {
    fun onFlowStarted(flowKey: String) {}
    fun onStepChanged(flowKey: String, stepIndex: Int) {}
    fun onFlowCompleted(flowKey: String) {}
    fun onFlowSkipped(flowKey: String) {}
    fun onAnchorMissing(flowKey: String, anchorKey: String) {}
    fun onError(error: GuideFlowError) {}
}
