package com.guideflow.backend

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.firebase.cloud.FirestoreClient
import com.guideflow.shared.AnalyticsEvent
import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.EventType
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialStep
import com.guideflow.shared.UpdateStepRequest
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Firestore-backed [GuideFlowStore]. Uses flat top-level collections — `projects`,
 * `flows`, `steps`, `publishedConfigs` — so flows/steps are addressable by their
 * global id with simple single-field queries (the routes use global ids).
 *
 * Firestore Admin calls are blocking (`ApiFuture.get()`); fine for the MVP's load.
 */
class FirestoreStore(
    private val db: Firestore = FirestoreClient.getFirestore(),
) : GuideFlowStore {

    private val json = Json { ignoreUnknownKeys = true }

    private val projects get() = db.collection("projects")
    private val flows get() = db.collection("flows")
    private val steps get() = db.collection("steps")
    private val publishedConfigs get() = db.collection("publishedConfigs")
    private val events get() = db.collection("events")
    private val summaries get() = db.collection("analyticsSummaries")

    // --- projects ------------------------------------------------------------

    override fun createProject(ownerUid: String, name: String): Pair<ProjectRecord, String> {
        val rawKey = ProjectKeys.generate()
        val record = ProjectRecord(
            projectId = "project_${shortId()}",
            ownerUid = ownerUid,
            name = name,
            projectKeyHash = ProjectKeys.hash(rawKey),
            configVersion = 0,
            createdAt = System.currentTimeMillis(),
        )
        projects.document(record.projectId).set(record.toMap()).get()
        return record to rawKey
    }

    override fun listProjects(ownerUid: String): List<ProjectRecord> =
        projects.whereEqualTo("ownerUid", ownerUid).get().get()
            .documents.map { it.toProjectRecord() }

    override fun getProject(projectId: String): ProjectRecord? =
        projects.document(projectId).get().get().takeIf { it.exists() }?.toProjectRecord()

    override fun findProjectByKeyHash(keyHash: String): ProjectRecord? =
        projects.whereEqualTo("projectKeyHash", keyHash).get().get()
            .documents.firstOrNull()?.toProjectRecord()

    override fun deleteProject(projectId: String): Boolean {
        if (projects.document(projectId).get().get().exists().not()) return false
        flows.whereEqualTo("projectId", projectId).get().get().documents.forEach { flowDoc ->
            steps.whereEqualTo("flowId", flowDoc.id).get().get().documents.forEach { it.reference.delete().get() }
            summaries.document(flowDoc.id).delete().get()
            flowDoc.reference.delete().get()
        }
        events.whereEqualTo("projectId", projectId).get().get().documents.forEach { it.reference.delete().get() }
        publishedConfigs.document(projectId).delete().get()
        projects.document(projectId).delete().get()
        return true
    }

    // --- flows ---------------------------------------------------------------

    override fun createFlow(projectId: String, flowKey: String, name: String): FlowRecord {
        if (projects.document(projectId).get().get().exists().not()) {
            throw ApiException(HttpStatusCode.NotFound, "project_not_found", "Unknown project $projectId")
        }
        val clash = flows.whereEqualTo("projectId", projectId).whereEqualTo("flowKey", flowKey).get().get()
        if (!clash.isEmpty) {
            throw ApiException(HttpStatusCode.Conflict, "flow_key_taken", "Flow key '$flowKey' already exists in this project")
        }
        val record = FlowRecord(
            flowId = "flow_${shortId()}",
            projectId = projectId,
            flowKey = flowKey,
            name = name,
            status = FlowStatus.DRAFT,
            steps = emptyList(),
            theme = FlowTheme.CLASSIC_LIGHT,
            themeDark = FlowTheme.CLASSIC_DARK,
        )
        flows.document(record.flowId).set(record.toMap()).get()
        return record
    }

    override fun listFlows(projectId: String): List<FlowRecord> =
        flows.whereEqualTo("projectId", projectId).get().get()
            .documents.map { it.toFlowRecord(loadSteps(it.id)) }

    override fun getFlow(flowId: String): FlowRecord? =
        flows.document(flowId).get().get().takeIf { it.exists() }?.toFlowRecord(loadSteps(flowId))

    override fun updateFlow(flowId: String, flowKey: String?, name: String?, theme: FlowTheme?, themeDark: FlowTheme?): FlowRecord? {
        val doc = flows.document(flowId).get().get().takeIf { it.exists() } ?: return null
        val current = doc.toFlowRecord(emptyList())
        if (flowKey != null && flowKey != current.flowKey) {
            val clash = flows.whereEqualTo("projectId", current.projectId).whereEqualTo("flowKey", flowKey).get().get()
            if (!clash.isEmpty) {
                throw ApiException(HttpStatusCode.Conflict, "flow_key_taken", "Flow key '$flowKey' already exists in this project")
            }
        }
        flows.document(flowId).update(
            mapOf(
                "flowKey" to (flowKey ?: current.flowKey),
                "name" to (name ?: current.name),
                "status" to draftedAgain(current.status).name,
                "themeJson" to json.encodeToString(theme ?: current.theme),
                "themeDarkJson" to json.encodeToString(themeDark ?: current.themeDark),
            ),
        ).get()
        return getFlow(flowId)
    }

    override fun deleteFlow(flowId: String): Boolean {
        if (flows.document(flowId).get().get().exists().not()) return false
        steps.whereEqualTo("flowId", flowId).get().get().documents.forEach { it.reference.delete().get() }
        flows.document(flowId).delete().get()
        return true
    }

    // --- steps ---------------------------------------------------------------

    override fun addStep(flowId: String, req: CreateStepRequest): TutorialStep? {
        if (flows.document(flowId).get().get().exists().not()) return null
        val existing = loadSteps(flowId)
        val order = req.order ?: ((existing.maxOfOrNull { it.order } ?: 0) + 1)
        val step = TutorialStep(
            id = "step_${shortId()}",
            order = order,
            type = req.type,
            anchorKey = req.anchorKey,
            title = req.title,
            body = req.body,
            advanceOnTap = req.advanceOnTap,
        )
        steps.document(step.id).set(step.toMap(flowId)).get()
        backToDraft(flowId)
        return step
    }

    override fun updateStep(stepId: String, req: UpdateStepRequest): TutorialStep? {
        val doc = steps.document(stepId).get().get().takeIf { it.exists() } ?: return null
        val flowId = doc.getString("flowId")!!
        val existing = doc.toStep()
        val updated = existing.copy(
            type = req.type ?: existing.type,
            anchorKey = if (req.anchorKey != null) req.anchorKey else existing.anchorKey,
            title = req.title ?: existing.title,
            body = req.body ?: existing.body,
            order = req.order ?: existing.order,
            advanceOnTap = req.advanceOnTap ?: existing.advanceOnTap,
        )
        steps.document(stepId).set(updated.toMap(flowId)).get()
        backToDraft(flowId)
        return updated
    }

    override fun deleteStep(stepId: String): Boolean {
        val doc = steps.document(stepId).get().get().takeIf { it.exists() } ?: return false
        val flowId = doc.getString("flowId")!!
        steps.document(stepId).delete().get()
        backToDraft(flowId)
        return true
    }

    override fun reorderSteps(flowId: String, orderedStepIds: List<String>): FlowRecord? {
        val current = loadSteps(flowId)
        if (current.isEmpty() && flows.document(flowId).get().get().exists().not()) return null
        if (orderedStepIds.toSet() != current.map { it.id }.toSet()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_order", "orderedStepIds must list exactly the flow's step ids")
        }
        val byId = current.associateBy { it.id }
        orderedStepIds.forEachIndexed { index, id ->
            steps.document(id).update("order", (index + 1).toLong()).get()
        }
        backToDraft(flowId)
        return getFlow(flowId)
    }

    override fun getFlowIdForStep(stepId: String): String? =
        steps.document(stepId).get().get().takeIf { it.exists() }?.getString("flowId")

    // --- publish / config ----------------------------------------------------

    override fun publishFlow(flowId: String): FlowRecord {
        val flow = getFlow(flowId) ?: throw ApiException(HttpStatusCode.NotFound, "flow_not_found", "Unknown flow $flowId")
        FlowValidator.validateForPublish(flow)

        flows.document(flowId).update("status", FlowStatus.PUBLISHED.name).get()

        val project = getProject(flow.projectId)
            ?: throw ApiException(HttpStatusCode.NotFound, "project_not_found", "Unknown project ${flow.projectId}")
        val newVersion = project.configVersion + 1
        projects.document(project.projectId).update("configVersion", newVersion.toLong()).get()

        val publishedFlows = listFlows(project.projectId).filter { it.status == FlowStatus.PUBLISHED }
        val config = ConfigCompiler.compile(project.projectId, newVersion, publishedFlows)
        publishedConfigs.document(project.projectId).set(mapOf("json" to json.encodeToString(config))).get()

        return getFlow(flowId)!!
    }

    override fun getPublishedConfig(projectId: String): TutorialConfig? {
        val doc = publishedConfigs.document(projectId).get().get().takeIf { it.exists() } ?: return null
        val raw = doc.getString("json") ?: return null
        return runCatching { json.decodeFromString<TutorialConfig>(raw) }.getOrNull()
    }

    // --- analytics -----------------------------------------------------------

    override fun recordEvents(projectId: String, events: List<AnalyticsEvent>): List<String> {
        for (e in events) {
            val ref = this.events.document(e.eventId)
            if (ref.get().get().exists()) continue // idempotent: counted already
            // ponytail: 1 read + 2 writes per event; fine for MVP volume, batch a write if it grows.
            ref.set(eventToMap(projectId, e)).get()
            val incr = summaryIncrement(e)
            if (incr.isNotEmpty()) {
                summaries.document(e.flowId).set(mapOf("flowId" to e.flowId) + incr, SetOptions.merge()).get()
            }
        }
        return events.map { it.eventId }
    }

    override fun getSummary(flowId: String): AnalyticsSummary {
        val doc = summaries.document(flowId).get().get().takeIf { it.exists() } ?: return AnalyticsSummary(flowId)
        @Suppress("UNCHECKED_CAST")
        val views = (doc.get("stepViews") as? Map<String, Any?>)
            ?.mapValues { (it.value as? Long ?: 0L).toInt() } ?: emptyMap()
        return AnalyticsSummary(
            flowId = flowId,
            started = (doc.getLong("started") ?: 0).toInt(),
            completed = (doc.getLong("completed") ?: 0).toInt(),
            skipped = (doc.getLong("skipped") ?: 0).toInt(),
            anchorMissing = (doc.getLong("anchorMissing") ?: 0).toInt(),
            stepViews = views,
        )
    }

    private fun summaryIncrement(e: AnalyticsEvent): Map<String, Any> = when (e.eventType) {
        EventType.FLOW_STARTED -> mapOf("started" to FieldValue.increment(1))
        EventType.FLOW_COMPLETED -> mapOf("completed" to FieldValue.increment(1))
        EventType.FLOW_SKIPPED -> mapOf("skipped" to FieldValue.increment(1))
        EventType.ANCHOR_MISSING -> mapOf("anchorMissing" to FieldValue.increment(1))
        EventType.STEP_SHOWN -> e.stepId?.let { sid ->
            mapOf("stepViews" to mapOf(sid to FieldValue.increment(1)))
        } ?: emptyMap()
        EventType.STEP_COMPLETED -> emptyMap()
    }

    private fun eventToMap(projectId: String, e: AnalyticsEvent): Map<String, Any?> = mapOf(
        "eventId" to e.eventId,
        "projectId" to projectId,
        "flowId" to e.flowId,
        "stepId" to e.stepId,
        "eventType" to e.eventType.name,
        "timestamp" to e.timestamp,
        "userIdHash" to e.userIdHash,
        "sessionId" to e.sessionId,
        "appVersion" to e.appVersion,
        "sdkVersion" to e.sdkVersion,
        "androidVersion" to e.androidVersion,
        "deviceModel" to e.deviceModel,
    )

    // --- helpers -------------------------------------------------------------

    private fun loadSteps(flowId: String): List<TutorialStep> =
        steps.whereEqualTo("flowId", flowId).get().get()
            .documents.map { it.toStep() }.sortedBy { it.order }

    /** Any structural edit invalidates a publish: return the flow to draft. */
    private fun backToDraft(flowId: String) {
        val status = flows.document(flowId).get().get().getString("status")
        if (status == FlowStatus.PUBLISHED.name) {
            flows.document(flowId).update("status", FlowStatus.DRAFT.name).get()
        }
    }

    private fun draftedAgain(status: FlowStatus): FlowStatus =
        if (status == FlowStatus.PUBLISHED) FlowStatus.DRAFT else status

    private fun shortId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun ProjectRecord.toMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "ownerUid" to ownerUid,
        "name" to name,
        "projectKeyHash" to projectKeyHash,
        "configVersion" to configVersion.toLong(),
        "createdAt" to createdAt,
    )

    private fun DocumentSnapshot.toProjectRecord() = ProjectRecord(
        projectId = getString("projectId") ?: id,
        ownerUid = getString("ownerUid").orEmpty(),
        name = getString("name").orEmpty(),
        projectKeyHash = getString("projectKeyHash").orEmpty(),
        configVersion = (getLong("configVersion") ?: 0L).toInt(),
        createdAt = getLong("createdAt") ?: 0L,
    )

    private fun FlowRecord.toMap(): Map<String, Any> = mapOf(
        "flowId" to flowId,
        "projectId" to projectId,
        "flowKey" to flowKey,
        "name" to name,
        "status" to status.name,
        "themeJson" to json.encodeToString(theme),
        "themeDarkJson" to json.encodeToString(themeDark),
    )

    private fun DocumentSnapshot.toFlowRecord(steps: List<TutorialStep>) = FlowRecord(
        flowId = getString("flowId") ?: id,
        projectId = getString("projectId").orEmpty(),
        flowKey = getString("flowKey").orEmpty(),
        name = getString("name").orEmpty(),
        status = FlowStatus.valueOf(getString("status") ?: FlowStatus.DRAFT.name),
        steps = steps,
        theme = getString("themeJson")?.let { runCatching { json.decodeFromString<FlowTheme>(it) }.getOrNull() } ?: FlowTheme(),
        themeDark = getString("themeDarkJson")?.let { runCatching { json.decodeFromString<FlowTheme>(it) }.getOrNull() } ?: FlowTheme(),
    )

    private fun TutorialStep.toMap(flowId: String): Map<String, Any?> = mapOf(
        "id" to id,
        "flowId" to flowId,
        "order" to order.toLong(),
        "type" to type.name,
        "anchorKey" to anchorKey,
        "title" to title,
        "body" to body,
        "advanceOnTap" to advanceOnTap,
    )

    private fun DocumentSnapshot.toStep() = TutorialStep(
        id = getString("id") ?: id,
        order = (getLong("order") ?: 0L).toInt(),
        type = StepType.valueOf(getString("type") ?: StepType.MODAL.name),
        anchorKey = getString("anchorKey"),
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        advanceOnTap = getBoolean("advanceOnTap") ?: false,
    )
}
