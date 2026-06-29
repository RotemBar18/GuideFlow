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

/**
 * Per-flow visual customization. All fields have defaults, so older configs
 * (and flows created before theming) deserialize unchanged.
 */
@Serializable
data class FlowTheme(
    val accentColor: String? = null,   // "#RRGGBB"; null = SDK default accent
    val dimOpacity: Float = 0.6f,      // 0..1, spotlight/modal scrim darkness
    val cornerRadius: Int = 14,        // dp for cards/bubbles
    val nextLabel: String = "Next",
    val skipLabel: String = "Skip",
    val doneLabel: String = "Done",
    val showProgress: Boolean = true,
    val showSkip: Boolean = true,
)

@Serializable
data class TutorialFlow(
    val id: String,
    val flowKey: String,
    val name: String,
    val status: FlowStatus,
    val steps: List<TutorialStep> = emptyList(),
    val theme: FlowTheme = FlowTheme(),
)

/** The single published configuration object downloaded by the SDK. */
@Serializable
data class TutorialConfig(
    val projectId: String,
    val configVersion: Int,
    val flows: List<TutorialFlow> = emptyList(),
)
