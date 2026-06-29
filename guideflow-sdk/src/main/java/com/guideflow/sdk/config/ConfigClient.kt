package com.guideflow.sdk.config

import com.guideflow.sdk.api.GuideFlowError
import com.guideflow.shared.TutorialConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Talks to the backend's single config endpoint: `GET /api/client/config`.
 * Sends the project key header and an optional `currentVersion` for 304 handling.
 * Never throws — network/parse failures become [ConfigResult.Failure].
 */
internal class ConfigClient(
    private val baseUrl: String,
) : ConfigSource {

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun fetch(projectKey: String, currentVersion: Int?): ConfigResult {
        return try {
            val response = httpClient.get("${baseUrl.trimEnd('/')}/api/client/config") {
                header(PROJECT_KEY_HEADER, projectKey)
                if (currentVersion != null) parameter("currentVersion", currentVersion)
            }
            when (response.status.value) {
                200 -> ConfigResult.Success(response.body<TutorialConfig>())
                304 -> ConfigResult.NotModified
                401, 404 -> ConfigResult.Failure(
                    GuideFlowError.InvalidConfig("No published config for the provided project key"),
                )
                else -> ConfigResult.Failure(GuideFlowError.NetworkError("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            ConfigResult.Failure(GuideFlowError.NetworkError(e.message ?: "Network error"))
        }
    }

    fun close() {
        httpClient.close()
    }

    private companion object {
        const val PROJECT_KEY_HEADER = "X-GuideFlow-Project-Key"
    }
}
