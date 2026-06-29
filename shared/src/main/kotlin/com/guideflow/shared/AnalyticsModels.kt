package com.guideflow.shared

import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    FLOW_STARTED,
    STEP_SHOWN,
    STEP_COMPLETED,
    FLOW_SKIPPED,
    FLOW_COMPLETED,
    ANCHOR_MISSING,
}

@Serializable
data class AnalyticsEvent(
    val eventId: String,
    val flowId: String,
    val stepId: String? = null,
    val eventType: EventType,
    val timestamp: Long,
    val userIdHash: String? = null,
    val sessionId: String,
    val appVersion: String? = null,
    val sdkVersion: String? = null,
    val androidVersion: String? = null,
    val deviceModel: String? = null,
)

@Serializable
data class AnalyticsBatch(val events: List<AnalyticsEvent>)

/** Server echoes the ids it stored; the SDK deletes those locally. */
@Serializable
data class AnalyticsBatchResponse(val acceptedEventIds: List<String>)

@Serializable
data class AnalyticsSummary(
    val flowId: String,
    val started: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val anchorMissing: Int = 0,
    val stepViews: Map<String, Int> = emptyMap(),
)
