package com.guideflow.portal

import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.CreateFlowRequest
import com.guideflow.shared.CreateProjectRequest
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.UpdateFlowRequest
import com.guideflow.shared.CreateProjectResponse
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.ProjectDto
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import com.guideflow.shared.UpdateStepRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Client for the GuideFlow backend REST API. A Firebase ID token is sent as a
 * Bearer header; in the backend's dev mode the token is ignored.
 *
 * On the Android emulator, 10.0.2.2 reaches the host; on a physical device use the
 * dev machine's LAN IP.
 */
class PortalApi(
    // Hosted backend (Cloud Run) — works on any network, no shared Wi-Fi needed.
    private val baseUrl: String = "https://guideflow-backend-794711970205.me-west1.run.app",
) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    // --- projects ---
    suspend fun listProjects(token: String?): List<ProjectDto> =
        http.get("$baseUrl/api/projects") { authorize(token) }.body()

    suspend fun createProject(name: String, token: String?): CreateProjectResponse =
        http.post("$baseUrl/api/projects") {
            authorize(token); contentType(ContentType.Application.Json); setBody(CreateProjectRequest(name))
        }.body()

    // --- flows ---
    suspend fun listFlows(projectId: String, token: String?): List<TutorialFlow> =
        http.get("$baseUrl/api/projects/$projectId/flows") { authorize(token) }.body()

    suspend fun createFlow(projectId: String, flowKey: String, name: String, token: String?): TutorialFlow =
        http.post("$baseUrl/api/projects/$projectId/flows") {
            authorize(token); contentType(ContentType.Application.Json); setBody(CreateFlowRequest(flowKey, name))
        }.body()

    suspend fun getFlow(flowId: String, token: String?): TutorialFlow =
        http.get("$baseUrl/api/flows/$flowId") { authorize(token) }.body()

    suspend fun deleteFlow(flowId: String, token: String?) {
        http.delete("$baseUrl/api/flows/$flowId") { authorize(token) }
    }

    suspend fun publishFlow(flowId: String, token: String?): TutorialFlow =
        http.post("$baseUrl/api/flows/$flowId/publish") { authorize(token) }.body()

    suspend fun getAnalytics(flowId: String, token: String?): AnalyticsSummary =
        http.get("$baseUrl/api/flows/$flowId/analytics") { authorize(token) }.body()

    suspend fun updateFlowThemes(flowId: String, light: FlowTheme, dark: FlowTheme, token: String?): TutorialFlow =
        http.put("$baseUrl/api/flows/$flowId") {
            authorize(token); contentType(ContentType.Application.Json); setBody(UpdateFlowRequest(theme = light, themeDark = dark))
        }.body()

    suspend fun renameFlow(flowId: String, name: String, token: String?): TutorialFlow =
        http.put("$baseUrl/api/flows/$flowId") {
            authorize(token); contentType(ContentType.Application.Json); setBody(UpdateFlowRequest(name = name))
        }.body()

    // --- steps ---
    suspend fun addStep(flowId: String, req: CreateStepRequest, token: String?): TutorialStep =
        http.post("$baseUrl/api/flows/$flowId/steps") {
            authorize(token); contentType(ContentType.Application.Json); setBody(req)
        }.body()

    suspend fun updateStep(stepId: String, req: UpdateStepRequest, token: String?): TutorialStep =
        http.put("$baseUrl/api/steps/$stepId") {
            authorize(token); contentType(ContentType.Application.Json); setBody(req)
        }.body()

    suspend fun deleteStep(stepId: String, token: String?) {
        http.delete("$baseUrl/api/steps/$stepId") { authorize(token) }
    }

    private fun HttpRequestBuilder.authorize(token: String?) {
        if (token != null) header("Authorization", "Bearer $token")
    }
}
