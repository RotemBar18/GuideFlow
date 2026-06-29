package com.guideflow.backend

import com.guideflow.shared.AnalyticsBatch
import com.guideflow.shared.AnalyticsBatchResponse
import com.guideflow.shared.AnalyticsEvent
import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.CreateFlowRequest
import com.guideflow.shared.CreateProjectRequest
import com.guideflow.shared.CreateProjectResponse
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.EventType
import com.guideflow.shared.StepType
import com.guideflow.backend.auth.DevAuthProvider
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialFlow
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyHeader = "X-GuideFlow-Project-Key"

    private fun ApplicationTestBuilder.installApp() {
        // Pin to in-memory + dev auth so these tests are deterministic even when
        // Firebase credentials are present in the environment.
        application { module(store = InMemoryStore(), auth = DevAuthProvider()) }
    }

    private suspend fun ApplicationTestBuilder.createProject(name: String = "Demo App"): CreateProjectResponse {
        val res = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateProjectRequest(name)))
        }
        assertEquals(HttpStatusCode.Created, res.status)
        return json.decodeFromString(res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.createFlow(projectId: String, flowKey: String): TutorialFlow {
        val res = client.post("/api/projects/$projectId/flows") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateFlowRequest(flowKey, "Tour")))
        }
        assertEquals(HttpStatusCode.Created, res.status)
        return json.decodeFromString(res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.addStep(flowId: String, req: CreateStepRequest) {
        val res = client.post("/api/flows/$flowId/steps") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(req))
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }

    private var seq = 0
    private fun ev(flowId: String, type: EventType, stepId: String? = null) = AnalyticsEvent(
        eventId = "e${seq++}", flowId = flowId, stepId = stepId, eventType = type,
        timestamp = 0, sessionId = "sess",
    )

    @Test
    fun analyticsBatch_updatesSummary() = testApplication {
        installApp()
        val created = createProject()
        val flow = createFlow(created.project.projectId, "tour")

        val batch = AnalyticsBatch(
            listOf(
                ev(flow.id, EventType.FLOW_STARTED),
                ev(flow.id, EventType.STEP_SHOWN, "s1"),
                ev(flow.id, EventType.STEP_SHOWN, "s1"),
                ev(flow.id, EventType.FLOW_COMPLETED),
            ),
        )
        val res = client.post("/api/client/events/batch") {
            header(keyHeader, created.projectKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(batch))
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(4, json.decodeFromString<AnalyticsBatchResponse>(res.bodyAsText()).acceptedEventIds.size)

        val summary = json.decodeFromString<AnalyticsSummary>(
            client.get("/api/flows/${flow.id}/analytics").bodyAsText(),
        )
        assertEquals(1, summary.started)
        assertEquals(1, summary.completed)
        assertEquals(2, summary.stepViews["s1"])
    }

    @Test
    fun createProject_returnsRawKeyAndProject() = testApplication {
        installApp()
        val created = createProject("Finance Demo")
        assertTrue(created.projectKey.startsWith("gf_"))
        assertEquals("Finance Demo", created.project.name)
        assertEquals(0, created.project.configVersion)
    }

    @Test
    fun publishedFlow_appearsInClientConfig() = testApplication {
        installApp()
        val created = createProject()
        val flow = createFlow(created.project.projectId, "budget_tutorial")
        addStep(flow.id, CreateStepRequest(StepType.MODAL, anchorKey = null, title = "Welcome", body = "Hi"))

        val publish = client.post("/api/flows/${flow.id}/publish")
        assertEquals(HttpStatusCode.OK, publish.status)

        val configRes = client.get("/api/client/config") { header(keyHeader, created.projectKey) }
        assertEquals(HttpStatusCode.OK, configRes.status)
        val config = json.decodeFromString<TutorialConfig>(configRes.bodyAsText())

        assertEquals(1, config.configVersion)
        assertEquals(1, config.flows.size)
        assertEquals("budget_tutorial", config.flows.first().flowKey)
        assertEquals("Welcome", config.flows.first().steps.first().title)
    }

    @Test
    fun clientConfig_withUnknownKey_returns404() = testApplication {
        installApp()
        val res = client.get("/api/client/config") { header(keyHeader, "gf_does_not_exist") }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun publish_emptyFlow_returns400() = testApplication {
        installApp()
        val created = createProject()
        val flow = createFlow(created.project.projectId, "empty_flow")
        val res = client.post("/api/flows/${flow.id}/publish")
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun publish_tooltipWithoutAnchor_returns400() = testApplication {
        installApp()
        val created = createProject()
        val flow = createFlow(created.project.projectId, "bad_flow")
        addStep(flow.id, CreateStepRequest(StepType.TOOLTIP, anchorKey = null, title = "T", body = "B"))
        val res = client.post("/api/flows/${flow.id}/publish")
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun clientConfig_withMatchingVersion_returns304() = testApplication {
        installApp()
        val created = createProject()
        val flow = createFlow(created.project.projectId, "tour")
        addStep(flow.id, CreateStepRequest(StepType.MODAL, anchorKey = null, title = "Welcome", body = "Hi"))
        client.post("/api/flows/${flow.id}/publish")

        val res = client.get("/api/client/config") {
            header(keyHeader, created.projectKey)
            url { parameters.append("currentVersion", "1") }
        }
        assertEquals(HttpStatusCode.NotModified, res.status)
    }
}
