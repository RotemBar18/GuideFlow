package com.guideflow.sdk.analytics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.guideflow.shared.AnalyticsBatch
import com.guideflow.shared.AnalyticsBatchResponse
import com.guideflow.shared.AnalyticsEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val BATCH_SIZE = 100
private const val UNIQUE_WORK = "guideflow_analytics_upload"
private const val KEY_BASE_URL = "baseUrl"
private const val KEY_PROJECT_KEY = "projectKey"
private val json = Json { ignoreUnknownKeys = true }

/**
 * Drains the local event queue to the backend. Deletes only the events the server
 * acknowledges, so a failed upload keeps them for the next run (CLAUDE.md rule).
 * Shared by [AnalyticsUploadWorker] and `GuideFlow.flush()`.
 */
internal suspend fun uploadPending(context: Context, baseUrl: String, projectKey: String): Int {
    val dao = EventDatabase.get(context).events()
    val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
    }
    var uploaded = 0
    try {
        while (true) {
            val rows = dao.oldest(BATCH_SIZE)
            if (rows.isEmpty()) break
            val events = rows.map { json.decodeFromString<AnalyticsEvent>(it.payloadJson) }
            val response: AnalyticsBatchResponse = client.post("${baseUrl.trimEnd('/')}/api/client/events/batch") {
                header("X-GuideFlow-Project-Key", projectKey)
                contentType(ContentType.Application.Json)
                setBody(AnalyticsBatch(events))
            }.body()
            if (response.acceptedEventIds.isEmpty()) break
            dao.deleteByIds(response.acceptedEventIds)
            uploaded += response.acceptedEventIds.size
            if (rows.size < BATCH_SIZE) break
        }
    } finally {
        client.close()
    }
    return uploaded
}

internal class AnalyticsUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.success()
        val projectKey = inputData.getString(KEY_PROJECT_KEY) ?: return Result.success()
        return try {
            uploadPending(applicationContext, baseUrl, projectKey)
            Result.success()
        } catch (e: Exception) {
            Result.retry() // network/server error: keep events, try later
        }
    }

    companion object {
        fun enqueue(context: Context, baseUrl: String, projectKey: String) {
            val request = OneTimeWorkRequestBuilder<AnalyticsUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_BASE_URL to baseUrl, KEY_PROJECT_KEY to projectKey))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
