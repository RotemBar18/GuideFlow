package com.guideflow.sdk.api

/**
 * All recoverable errors the SDK can surface. The SDK never throws these at the
 * host app; they are reported through [GuideFlowListener.onError] or wrapped in a
 * failed [Result]. See CLAUDE.md → "Error Handling".
 */
sealed class GuideFlowError {
    data object NotInitialized : GuideFlowError()
    data class FlowNotFound(val flowKey: String) : GuideFlowError()
    data class AnchorMissing(val anchorKey: String) : GuideFlowError()
    data class NetworkError(val message: String) : GuideFlowError()
    data class InvalidConfig(val message: String) : GuideFlowError()
}

/** Wraps a [GuideFlowError] so it can travel inside a failed [Result]. */
class GuideFlowException(val error: GuideFlowError) : Exception(error.toString())
