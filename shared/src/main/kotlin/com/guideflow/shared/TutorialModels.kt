package com.guideflow.shared

import kotlinx.serialization.Serializable

/**
 * Shared, serializable tutorial-config models used by the SDK, backend, and portal.
 *
 * These mirror the "Tutorial Config Model" in CLAUDE.md. Only the config-side
 * models live here for now; analytics DTOs are added in a later phase.
 */

@Serializable
enum class StepType {
    TOOLTIP,
    SPOTLIGHT,
    MODAL,
}

@Serializable
enum class FlowStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
}

@Serializable
data class TutorialStep(
    val id: String,
    val order: Int,
    val type: StepType,
    val anchorKey: String? = null,
    val title: String,
    val body: String,
)

@Serializable
data class TutorialFlow(
    val id: String,
    val flowKey: String,
    val name: String,
    val status: FlowStatus,
    val steps: List<TutorialStep> = emptyList(),
)

/** The single published configuration object downloaded by the SDK. */
@Serializable
data class TutorialConfig(
    val projectId: String,
    val configVersion: Int,
    val flows: List<TutorialFlow> = emptyList(),
)
