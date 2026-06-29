package com.guideflow.sdk.analytics

import android.content.Context
import android.os.Build
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.shared.AnalyticsEvent
import com.guideflow.shared.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Creates analytics events, queues them in Room, and schedules a WorkManager upload.
 * One instance per SDK session; [sessionId] is stable for the process.
 */
internal class AnalyticsManager(
    context: Context,
    private val baseUrl: String,
    private val projectKey: String,
    private val userHashProvider: () -> String?,
) {
    private val appContext = context.applicationContext
    private val dao = EventDatabase.get(appContext).events()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionId = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appVersion = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull()

    fun record(flowId: String, stepId: String?, type: EventType) {
        val event = AnalyticsEvent(
            eventId = UUID.randomUUID().toString(),
            flowId = flowId,
            stepId = stepId,
            eventType = type,
            timestamp = System.currentTimeMillis(),
            userIdHash = userHashProvider(),
            sessionId = sessionId,
            appVersion = appVersion,
            sdkVersion = GuideFlow.SDK_VERSION,
            androidVersion = Build.VERSION.RELEASE,
            deviceModel = Build.MODEL,
        )
        scope.launch {
            dao.insert(AnalyticsEventEntity(event.eventId, event.timestamp, json.encodeToString(event)))
            val over = dao.count() - MAX_EVENTS
            if (over > 0) dao.deleteOldest(over)
            AnalyticsUploadWorker.enqueue(appContext, baseUrl, projectKey)
        }
    }

    suspend fun flush(): Result<Int> =
        runCatching { uploadPending(appContext, baseUrl, projectKey) }

    private companion object {
        const val MAX_EVENTS = 1000 // CLAUDE.md cap; oldest dropped first
    }
}
