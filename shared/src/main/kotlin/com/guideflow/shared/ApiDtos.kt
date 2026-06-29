package com.guideflow.shared

import kotlinx.serialization.Serializable

/**
 * HTTP request/response DTOs shared between the backend and (later) the portal.
 * Flow/step responses reuse [TutorialFlow] / [TutorialStep] from the config model
 * rather than introducing parallel types.
 */

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

@Serializable
data class ProjectDto(
    val projectId: String,
    val name: String,
    val configVersion: Int,
    val createdAt: Long,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
)

/** Returned once on project creation; [projectKey] is the raw key, shown only here. */
@Serializable
data class CreateProjectResponse(
    val project: ProjectDto,
    val projectKey: String,
)

@Serializable
data class CreateFlowRequest(
    val flowKey: String,
    val name: String,
)

@Serializable
data class UpdateFlowRequest(
    val flowKey: String? = null,
    val name: String? = null,
    val theme: FlowTheme? = null,
)

@Serializable
data class CreateStepRequest(
    val type: StepType,
    val anchorKey: String? = null,
    val title: String,
    val body: String,
    val order: Int? = null,
)

@Serializable
data class UpdateStepRequest(
    val type: StepType? = null,
    val anchorKey: String? = null,
    val title: String? = null,
    val body: String? = null,
    val order: Int? = null,
)

@Serializable
data class ReorderStepsRequest(
    val orderedStepIds: List<String>,
)
