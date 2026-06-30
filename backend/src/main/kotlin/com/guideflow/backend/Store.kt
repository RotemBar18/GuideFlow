package com.guideflow.backend

import com.guideflow.shared.AnalyticsEvent
import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.EventType
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import com.guideflow.shared.UpdateStepRequest
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** A stored project. The raw project key is never kept — only [projectKeyHash]. */
data class ProjectRecord(
    val projectId: String,
    val ownerUid: String,
    val name: String,
    val projectKeyHash: String,
    val configVersion: Int,
    val createdAt: Long,
)

/** A stored flow with its steps (used for editing; published flows feed the config). */
data class FlowRecord(
    val flowId: String,
    val projectId: String,
    val flowKey: String,
    val name: String,
    val status: FlowStatus,
    val steps: List<TutorialStep>,
    val theme: FlowTheme = FlowTheme(),
    val themeDark: FlowTheme = FlowTheme(),
)

/**
 * Persistence abstraction for the backend. The MVP ships an [InMemoryStore]; a
 * Firestore-backed implementation can replace it in Phase 4 without touching routes.
 */
interface GuideFlowStore {
    fun createProject(ownerUid: String, name: String): Pair<ProjectRecord, String>
    fun listProjects(ownerUid: String): List<ProjectRecord>
    fun getProject(projectId: String): ProjectRecord?
    fun findProjectByKeyHash(keyHash: String): ProjectRecord?
    /** Delete a project and everything under it (flows, steps, published config, analytics). */
    fun deleteProject(projectId: String): Boolean

    fun createFlow(projectId: String, flowKey: String, name: String): FlowRecord
    fun listFlows(projectId: String): List<FlowRecord>
    fun getFlow(flowId: String): FlowRecord?
    fun updateFlow(flowId: String, flowKey: String?, name: String?, theme: FlowTheme?, themeDark: FlowTheme?): FlowRecord?
    fun deleteFlow(flowId: String): Boolean

    fun addStep(flowId: String, req: CreateStepRequest): TutorialStep?
    fun updateStep(stepId: String, req: UpdateStepRequest): TutorialStep?
    fun deleteStep(stepId: String): Boolean
    fun reorderSteps(flowId: String, orderedStepIds: List<String>): FlowRecord?
    fun getFlowIdForStep(stepId: String): String?

    fun publishFlow(flowId: String): FlowRecord
    fun getPublishedConfig(projectId: String): TutorialConfig?

    /** Store events (idempotent by eventId), update summaries, return accepted ids. */
    fun recordEvents(projectId: String, events: List<AnalyticsEvent>): List<String>
    fun getSummary(flowId: String): AnalyticsSummary
}

/**
 * Thread-safe in-memory store guarded by a single lock. Sufficient for local
 * development and tests; not durable across restarts.
 */
class InMemoryStore : GuideFlowStore {

    private val lock = Any()
    private val projects = LinkedHashMap<String, ProjectRecord>()
    private val flows = LinkedHashMap<String, FlowRecord>()
    private val publishedConfigs = HashMap<String, TutorialConfig>()
    private val summaries = HashMap<String, AnalyticsSummary>()
    private val seenEvents = HashSet<String>() // ponytail: unbounded; fine for dev/in-memory

    override fun createProject(ownerUid: String, name: String): Pair<ProjectRecord, String> = synchronized(lock) {
        val rawKey = ProjectKeys.generate()
        val record = ProjectRecord(
            projectId = "project_${shortId()}",
            ownerUid = ownerUid,
            name = name,
            projectKeyHash = ProjectKeys.hash(rawKey),
            configVersion = 0,
            createdAt = System.currentTimeMillis(),
        )
        projects[record.projectId] = record
        record to rawKey
    }

    override fun listProjects(ownerUid: String): List<ProjectRecord> = synchronized(lock) {
        projects.values.filter { it.ownerUid == ownerUid }
    }

    override fun getProject(projectId: String): ProjectRecord? = synchronized(lock) {
        projects[projectId]
    }

    override fun findProjectByKeyHash(keyHash: String): ProjectRecord? = synchronized(lock) {
        projects.values.firstOrNull { it.projectKeyHash == keyHash }
    }

    override fun deleteProject(projectId: String): Boolean = synchronized(lock) {
        if (projects.remove(projectId) == null) return@synchronized false
        flows.values.filter { it.projectId == projectId }.map { it.flowId }.forEach {
            flows.remove(it); summaries.remove(it)
        }
        publishedConfigs.remove(projectId)
        true
    }

    override fun recordEvents(projectId: String, events: List<AnalyticsEvent>): List<String> = synchronized(lock) {
        for (e in events) {
            if (!seenEvents.add(e.eventId)) continue // already counted
            var s = summaries[e.flowId] ?: AnalyticsSummary(e.flowId)
            s = when (e.eventType) {
                EventType.FLOW_STARTED -> s.copy(started = s.started + 1)
                EventType.FLOW_COMPLETED -> s.copy(completed = s.completed + 1)
                EventType.FLOW_SKIPPED -> s.copy(skipped = s.skipped + 1)
                EventType.ANCHOR_MISSING -> s.copy(anchorMissing = s.anchorMissing + 1)
                EventType.STEP_SHOWN -> e.stepId?.let { sid ->
                    s.copy(stepViews = s.stepViews + (sid to ((s.stepViews[sid] ?: 0) + 1)))
                } ?: s
                EventType.STEP_COMPLETED -> s
            }
            summaries[e.flowId] = s
        }
        events.map { it.eventId }
    }

    override fun getSummary(flowId: String): AnalyticsSummary = synchronized(lock) {
        summaries[flowId] ?: AnalyticsSummary(flowId)
    }

    override fun createFlow(projectId: String, flowKey: String, name: String): FlowRecord = synchronized(lock) {
        projects[projectId] ?: throw ApiException(HttpStatusCode.NotFound, "project_not_found", "Unknown project $projectId")
        if (flows.values.any { it.projectId == projectId && it.flowKey == flowKey }) {
            throw ApiException(HttpStatusCode.Conflict, "flow_key_taken", "Flow key '$flowKey' already exists in this project")
        }
        val record = FlowRecord(
            flowId = "flow_${shortId()}",
            projectId = projectId,
            flowKey = flowKey,
            name = name,
            status = FlowStatus.DRAFT,
            steps = emptyList(),
        )
        flows[record.flowId] = record
        record
    }

    override fun listFlows(projectId: String): List<FlowRecord> = synchronized(lock) {
        flows.values.filter { it.projectId == projectId }
    }

    override fun getFlow(flowId: String): FlowRecord? = synchronized(lock) {
        flows[flowId]
    }

    override fun updateFlow(flowId: String, flowKey: String?, name: String?, theme: FlowTheme?, themeDark: FlowTheme?): FlowRecord? = synchronized(lock) {
        val flow = flows[flowId] ?: return null
        if (flowKey != null && flowKey != flow.flowKey &&
            flows.values.any { it.projectId == flow.projectId && it.flowKey == flowKey }
        ) {
            throw ApiException(HttpStatusCode.Conflict, "flow_key_taken", "Flow key '$flowKey' already exists in this project")
        }
        val updated = flow.copy(
            flowKey = flowKey ?: flow.flowKey,
            name = name ?: flow.name,
            theme = theme ?: flow.theme,
            themeDark = themeDark ?: flow.themeDark,
            // Editing a published flow returns it to draft until re-published.
            status = if (flow.status == FlowStatus.PUBLISHED) FlowStatus.DRAFT else flow.status,
        )
        flows[flowId] = updated
        updated
    }

    override fun deleteFlow(flowId: String): Boolean = synchronized(lock) {
        flows.remove(flowId) != null
    }

    override fun addStep(flowId: String, req: CreateStepRequest): TutorialStep? = synchronized(lock) {
        val flow = flows[flowId] ?: return null
        val order = req.order ?: ((flow.steps.maxOfOrNull { it.order } ?: 0) + 1)
        val step = TutorialStep(
            id = "step_${shortId()}",
            order = order,
            type = req.type,
            anchorKey = req.anchorKey,
            title = req.title,
            body = req.body,
            advanceOnTap = req.advanceOnTap,
        )
        flows[flowId] = flow.copy(steps = flow.steps + step, status = draftedAgain(flow.status))
        step
    }

    override fun updateStep(stepId: String, req: UpdateStepRequest): TutorialStep? = synchronized(lock) {
        val flow = flows.values.firstOrNull { f -> f.steps.any { it.id == stepId } } ?: return null
        val existing = flow.steps.first { it.id == stepId }
        val updated = existing.copy(
            type = req.type ?: existing.type,
            anchorKey = if (req.anchorKey != null) req.anchorKey else existing.anchorKey,
            title = req.title ?: existing.title,
            body = req.body ?: existing.body,
            order = req.order ?: existing.order,
            advanceOnTap = req.advanceOnTap ?: existing.advanceOnTap,
        )
        flows[flow.flowId] = flow.copy(
            steps = flow.steps.map { if (it.id == stepId) updated else it },
            status = draftedAgain(flow.status),
        )
        updated
    }

    override fun deleteStep(stepId: String): Boolean = synchronized(lock) {
        val flow = flows.values.firstOrNull { f -> f.steps.any { it.id == stepId } } ?: return false
        flows[flow.flowId] = flow.copy(
            steps = flow.steps.filterNot { it.id == stepId },
            status = draftedAgain(flow.status),
        )
        true
    }

    override fun getFlowIdForStep(stepId: String): String? = synchronized(lock) {
        flows.values.firstOrNull { f -> f.steps.any { it.id == stepId } }?.flowId
    }

    override fun reorderSteps(flowId: String, orderedStepIds: List<String>): FlowRecord? = synchronized(lock) {
        val flow = flows[flowId] ?: return null
        val byId = flow.steps.associateBy { it.id }
        if (orderedStepIds.toSet() != byId.keys) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_order", "orderedStepIds must list exactly the flow's step ids")
        }
        val reordered = orderedStepIds.mapIndexed { index, id -> byId.getValue(id).copy(order = index + 1) }
        val updated = flow.copy(steps = reordered, status = draftedAgain(flow.status))
        flows[flowId] = updated
        updated
    }

    override fun publishFlow(flowId: String): FlowRecord = synchronized(lock) {
        val flow = flows[flowId] ?: throw ApiException(HttpStatusCode.NotFound, "flow_not_found", "Unknown flow $flowId")
        FlowValidator.validateForPublish(flow)

        val published = flow.copy(status = FlowStatus.PUBLISHED)
        flows[flowId] = published

        val project = projects.getValue(flow.projectId)
        val newVersion = project.configVersion + 1
        projects[project.projectId] = project.copy(configVersion = newVersion)

        val publishedFlows = flows.values.filter { it.projectId == project.projectId && it.status == FlowStatus.PUBLISHED }
        publishedConfigs[project.projectId] = ConfigCompiler.compile(project.projectId, newVersion, publishedFlows)
        published
    }

    override fun getPublishedConfig(projectId: String): TutorialConfig? = synchronized(lock) {
        publishedConfigs[projectId]
    }

    /** Any structural edit invalidates a publish: return the flow to draft. */
    private fun draftedAgain(status: FlowStatus): FlowStatus =
        if (status == FlowStatus.PUBLISHED) FlowStatus.DRAFT else status

    private fun shortId(): String = UUID.randomUUID().toString().substring(0, 8)
}

/** Publish-time validation mirroring CLAUDE.md → "Publishing". */
object FlowValidator {
    fun validateForPublish(flow: FlowRecord) {
        if (flow.steps.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "empty_flow", "A flow needs at least one step to publish")
        }
        val orders = flow.steps.map { it.order }
        if (orders.size != orders.toSet().size) {
            throw ApiException(HttpStatusCode.BadRequest, "duplicate_order", "Step order values must be unique")
        }
        flow.steps.forEach { step ->
            if (step.title.isBlank() || step.body.isBlank()) {
                throw ApiException(HttpStatusCode.BadRequest, "missing_fields", "Step '${step.id}' is missing a title or body")
            }
            val needsAnchor = step.type == StepType.TOOLTIP || step.type == StepType.SPOTLIGHT
            if (needsAnchor && step.anchorKey.isNullOrBlank()) {
                throw ApiException(HttpStatusCode.BadRequest, "missing_anchor", "Step '${step.id}' (${step.type}) requires an anchorKey")
            }
        }
    }
}

/** Builds the single published config payload from a project's published flows. */
object ConfigCompiler {
    fun compile(projectId: String, configVersion: Int, publishedFlows: List<FlowRecord>): TutorialConfig =
        TutorialConfig(
            projectId = projectId,
            configVersion = configVersion,
            flows = publishedFlows.map { flow ->
                TutorialFlow(
                    id = flow.flowId,
                    flowKey = flow.flowKey,
                    name = flow.name,
                    status = FlowStatus.PUBLISHED,
                    steps = flow.steps.sortedBy { it.order },
                    theme = flow.theme,
                    themeDark = flow.themeDark,
                )
            },
        )
}
