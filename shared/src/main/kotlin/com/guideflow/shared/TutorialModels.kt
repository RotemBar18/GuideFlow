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
    val advanceOnTap: Boolean = false, // tapping the anchor advances the flow; no Next button
)

/**
 * Per-flow visual customization. All fields have defaults, so older configs
 * (and flows created before theming) deserialize unchanged.
 */
@Serializable
data class FlowTheme(
    val accentColor: String? = null,       // "#RRGGBB"; null = SDK default accent
    val buttonTextColor: String? = null,   // text on the accent (Next/Done) button; null = white
    val backgroundColor: String? = null,   // card background; null = follow the device light/dark theme
    val rtl: Boolean = false,              // right-to-left layout for the overlay
    val dimOpacity: Float = 0.6f,          // 0..1, spotlight/modal scrim darkness
    val cornerRadius: Int = 14,            // dp for cards/bubbles
    val textColor: String? = null,         // title + body text; null = follow the device/card
    val titleSize: Int = 16,               // sp; font follows the host app's theme
    val bodySize: Int = 14,                // sp
    val nextLabel: String = "Next",
    val backLabel: String = "Back",
    val skipLabel: String = "Skip",
    val doneLabel: String = "Done",
    val progressFormat: String = "Step {current} of {total}", // {current}/{total} placeholders
    val showProgress: Boolean = true,
    val showSkip: Boolean = true,
    val showBack: Boolean = true, // hide Back for flows that change screens (Back can't un-navigate)
) {
    companion object {
        /** Classic light theme applied to a newly created flow's light variant. */
        val CLASSIC_LIGHT = FlowTheme(
            accentColor = "#4F5BD5",
            buttonTextColor = "#FFFFFF",
            backgroundColor = "#FFFFFF",
            textColor = "#11141B",
        )

        /** Classic dark theme applied to a newly created flow's dark variant. */
        val CLASSIC_DARK = FlowTheme(
            accentColor = "#7C3AED",
            buttonTextColor = "#FFFFFF",
            backgroundColor = "#1B1F27",
            textColor = "#FFFFFF",
        )
    }
}

/** Renders the step counter from [FlowTheme.progressFormat], substituting the placeholders. */
fun FlowTheme.progressText(current: Int, total: Int): String =
    progressFormat.replace("{current}", current.toString()).replace("{total}", total.toString())
// Step text follows the device theme; backgroundColor overrides the card surface when set.

@Serializable
data class TutorialFlow(
    val id: String,
    val flowKey: String,
    val name: String,
    val status: FlowStatus,
    val steps: List<TutorialStep> = emptyList(),
    val theme: FlowTheme = FlowTheme(),        // light-mode design
    val themeDark: FlowTheme = FlowTheme(),    // dark-mode design; used when the device is in dark mode
)

/** The single published configuration object downloaded by the SDK. */
@Serializable
data class TutorialConfig(
    val projectId: String,
    val configVersion: Int,
    val flows: List<TutorialFlow> = emptyList(),
)
