package com.guideflow.backend

import com.guideflow.backend.auth.AuthProvider
import com.guideflow.shared.AnalyticsBatch
import com.guideflow.shared.AnalyticsBatchResponse
import com.guideflow.shared.CreateFlowRequest
import com.guideflow.shared.CreateProjectRequest
import com.guideflow.shared.CreateProjectResponse
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.ProjectDto
import com.guideflow.shared.ReorderStepsRequest
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.UpdateFlowRequest
import com.guideflow.shared.UpdateStepRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

private const val PROJECT_KEY_HEADER = "X-GuideFlow-Project-Key"

fun Application.configureRouting(store: GuideFlowStore, auth: AuthProvider) {
    routing {
        route("/api") {
            projectRoutes(store, auth)
            flowRoutes(store, auth)
            stepRoutes(store, auth)
            clientRoutes(store)
        }
    }
}

// --- auth + ownership helpers -----------------------------------------------

/** The verified portal-user uid for this request (throws 401 if unauthenticated). */
private fun RoutingCall.owner(auth: AuthProvider): String =
    auth.requireUid(request.headers["Authorization"])

private fun GuideFlowStore.assertProjectOwned(projectId: String, owner: String): ProjectRecord {
    val project = getProject(projectId) ?: notFound("project_not_found", "Unknown project")
    if (project.ownerUid != owner) forbidden()
    return project
}

private fun GuideFlowStore.assertFlowOwned(flowId: String, owner: String): FlowRecord {
    val flow = getFlow(flowId) ?: notFound("flow_not_found", "Unknown flow")
    assertProjectOwned(flow.projectId, owner)
    return flow
}

// --- routes -----------------------------------------------------------------

private fun ProjectRecord.toDto() = ProjectDto(
    projectId = projectId,
    name = name,
    configVersion = configVersion,
    createdAt = createdAt,
)

private fun io.ktor.server.routing.Route.projectRoutes(store: GuideFlowStore, auth: AuthProvider) {
    route("/projects") {
        post {
            val owner = call.owner(auth)
            val body = call.receive<CreateProjectRequest>()
            if (body.name.isBlank()) badRequest("invalid_name", "Project name must not be blank")
            val (record, rawKey) = store.createProject(owner, body.name)
            call.respond(HttpStatusCode.Created, CreateProjectResponse(record.toDto(), rawKey))
        }
        get {
            val owner = call.owner(auth)
            call.respond(store.listProjects(owner).map { it.toDto() })
        }
        get("/{projectId}") {
            val owner = call.owner(auth)
            call.respond(store.assertProjectOwned(call.pathParam("projectId"), owner).toDto())
        }
        post("/{projectId}/flows") {
            val owner = call.owner(auth)
            val projectId = call.pathParam("projectId")
            store.assertProjectOwned(projectId, owner)
            val body = call.receive<CreateFlowRequest>()
            if (body.flowKey.isBlank()) badRequest("invalid_flow_key", "flowKey must not be blank")
            call.respond(HttpStatusCode.Created, store.createFlow(projectId, body.flowKey, body.name).toTutorialFlow())
        }
        get("/{projectId}/flows") {
            val owner = call.owner(auth)
            val projectId = call.pathParam("projectId")
            store.assertProjectOwned(projectId, owner)
            call.respond(store.listFlows(projectId).map { it.toTutorialFlow() })
        }
    }
}

private fun io.ktor.server.routing.Route.flowRoutes(store: GuideFlowStore, auth: AuthProvider) {
    route("/flows/{flowId}") {
        get {
            val owner = call.owner(auth)
            call.respond(store.assertFlowOwned(call.pathParam("flowId"), owner).toTutorialFlow())
        }
        put {
            val owner = call.owner(auth)
            val flowId = call.pathParam("flowId")
            store.assertFlowOwned(flowId, owner)
            val body = call.receive<UpdateFlowRequest>()
            val flow = store.updateFlow(flowId, body.flowKey, body.name, body.theme, body.themeDark) ?: notFound("flow_not_found", "Unknown flow")
            call.respond(flow.toTutorialFlow())
        }
        delete {
            val owner = call.owner(auth)
            val flowId = call.pathParam("flowId")
            store.assertFlowOwned(flowId, owner)
            store.deleteFlow(flowId)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/publish") {
            val owner = call.owner(auth)
            val flowId = call.pathParam("flowId")
            store.assertFlowOwned(flowId, owner)
            call.respond(store.publishFlow(flowId).toTutorialFlow())
        }
        get("/analytics") {
            val owner = call.owner(auth)
            val flow = store.assertFlowOwned(call.pathParam("flowId"), owner)
            call.respond(store.getSummary(flow.flowId))
        }
        post("/steps") {
            val owner = call.owner(auth)
            val flowId = call.pathParam("flowId")
            store.assertFlowOwned(flowId, owner)
            val body = call.receive<CreateStepRequest>()
            val step = store.addStep(flowId, body) ?: notFound("flow_not_found", "Unknown flow")
            call.respond(HttpStatusCode.Created, step)
        }
        put("/steps/order") {
            val owner = call.owner(auth)
            val flowId = call.pathParam("flowId")
            store.assertFlowOwned(flowId, owner)
            val body = call.receive<ReorderStepsRequest>()
            val flow = store.reorderSteps(flowId, body.orderedStepIds) ?: notFound("flow_not_found", "Unknown flow")
            call.respond(flow.toTutorialFlow())
        }
    }
}

private fun io.ktor.server.routing.Route.stepRoutes(store: GuideFlowStore, auth: AuthProvider) {
    route("/steps/{stepId}") {
        put {
            val owner = call.owner(auth)
            val stepId = call.pathParam("stepId")
            store.assertStepOwned(stepId, owner)
            val body = call.receive<UpdateStepRequest>()
            val step = store.updateStep(stepId, body) ?: notFound("step_not_found", "Unknown step")
            call.respond(step)
        }
        delete {
            val owner = call.owner(auth)
            val stepId = call.pathParam("stepId")
            store.assertStepOwned(stepId, owner)
            store.deleteStep(stepId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun GuideFlowStore.assertStepOwned(stepId: String, owner: String) {
    val flowId = getFlowIdForStep(stepId) ?: notFound("step_not_found", "Unknown step")
    assertFlowOwned(flowId, owner)
}

private fun io.ktor.server.routing.Route.clientRoutes(store: GuideFlowStore) {
    // The SDK's single config request — authenticated by project key, not Firebase.
    get("/client/config") {
        val rawKey = call.request.headers[PROJECT_KEY_HEADER]
            ?: badRequest("missing_project_key", "Missing $PROJECT_KEY_HEADER header")
        val project = store.findProjectByKeyHash(ProjectKeys.hash(rawKey))
            ?: notFound("project_not_found", "No project matches the provided key")

        val currentVersion = call.request.queryParameters["currentVersion"]?.toIntOrNull()
        if (currentVersion != null && currentVersion == project.configVersion) {
            call.respond(HttpStatusCode.NotModified)
            return@get
        }

        val config = store.getPublishedConfig(project.projectId)
            ?: TutorialConfig(project.projectId, project.configVersion, emptyList())
        call.respond(config)
    }

    // SDK analytics upload — project-key auth, like config.
    post("/client/events/batch") {
        val rawKey = call.request.headers[PROJECT_KEY_HEADER]
            ?: badRequest("missing_project_key", "Missing $PROJECT_KEY_HEADER header")
        val project = store.findProjectByKeyHash(ProjectKeys.hash(rawKey))
            ?: notFound("project_not_found", "No project matches the provided key")
        val batch = call.receive<AnalyticsBatch>()
        call.respond(AnalyticsBatchResponse(store.recordEvents(project.projectId, batch.events)))
    }
}

private fun FlowRecord.toTutorialFlow() = TutorialFlow(
    id = flowId,
    flowKey = flowKey,
    name = name,
    status = status,
    steps = steps.sortedBy { it.order },
    theme = theme,
    themeDark = themeDark,
)

private fun RoutingCall.pathParam(name: String): String =
    pathParameters[name] ?: throw ApiException(HttpStatusCode.BadRequest, "missing_param", "Missing path parameter '$name'")

private fun notFound(code: String, message: String): Nothing =
    throw ApiException(HttpStatusCode.NotFound, code, message)

private fun badRequest(code: String, message: String): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, code, message)

private fun forbidden(): Nothing =
    throw ApiException(HttpStatusCode.Forbidden, "forbidden", "You do not have access to this resource")
