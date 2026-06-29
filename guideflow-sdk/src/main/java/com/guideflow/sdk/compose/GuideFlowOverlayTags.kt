package com.guideflow.sdk.compose

/**
 * Stable Compose `testTag`s for overlays and controls, so UI tests can assert
 * which overlay rendered and drive the controls regardless of their label.
 */
internal object GuideFlowOverlayTags {
    const val TOOLTIP = "guideflow_tooltip"
    const val SPOTLIGHT = "guideflow_spotlight"
    const val MODAL = "guideflow_modal"

    const val NEXT = "guideflow_next"
    const val BACK = "guideflow_back"
    const val SKIP = "guideflow_skip"
}
