package com.guideflow.sdk.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.api.GuideFlowListener
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the overlay engine. They drive the public
 * surface — [GuideFlow.startFlow] + `Modifier.guideFlowAnchor` inside
 * [GuideFlowHost] — and assert which overlay renders and how the controls behave.
 *
 * Requires a connected device/emulator: `:guideflow-sdk:connectedDebugAndroidTest`.
 */
class OverlayUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun reset() {
        // GuideFlow is a singleton object; clear state between tests.
        GuideFlow.stopFlow()
        GuideFlow.setListener(null)
        GuideFlow.loadLocalFlows(emptyList())
    }

    // --- helpers -------------------------------------------------------------

    private fun step(id: String, order: Int, type: StepType, anchorKey: String?, title: String) =
        TutorialStep(
            id = id,
            order = order,
            type = type,
            anchorKey = anchorKey,
            title = title,
            body = "$title body",
        )

    private fun flow(vararg steps: TutorialStep) = TutorialFlow(
        id = "flow",
        flowKey = "tour",
        name = "Tour",
        status = FlowStatus.PUBLISHED,
        steps = steps.toList(),
    )

    /** Host with two registered anchors. Layout settles so anchors are measured. */
    private fun ComposeContentTestRule.setHostWithAnchors() {
        setContent {
            MaterialTheme {
                GuideFlowHost {
                    Column {
                        Box(Modifier.size(64.dp).guideFlowAnchor("anchor_a"))
                        Box(Modifier.size(64.dp).guideFlowAnchor("anchor_b"))
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun startFlow() {
        rule.runOnIdle { GuideFlow.startFlow("tour") }
        rule.waitForIdle()
    }

    // --- tests ---------------------------------------------------------------

    @Test
    fun tooltipOverlay_isShown_forTooltipStep() {
        GuideFlow.loadLocalFlows(listOf(flow(step("s1", 1, StepType.TOOLTIP, "anchor_a", "Tooltip Step"))))
        rule.setHostWithAnchors()

        startFlow()

        rule.onNodeWithTag(GuideFlowOverlayTags.TOOLTIP).assertIsDisplayed()
        rule.onNodeWithText("Tooltip Step").assertIsDisplayed()
    }

    @Test
    fun spotlightOverlay_isShown_forSpotlightStep() {
        GuideFlow.loadLocalFlows(listOf(flow(step("s1", 1, StepType.SPOTLIGHT, "anchor_a", "Spotlight Step"))))
        rule.setHostWithAnchors()

        startFlow()

        rule.onNodeWithTag(GuideFlowOverlayTags.SPOTLIGHT).assertIsDisplayed()
        rule.onNodeWithText("Spotlight Step").assertIsDisplayed()
    }

    @Test
    fun modalFallback_isShown_andEmitted_whenAnchorMissing() {
        var missingAnchor: String? = null
        GuideFlow.setListener(object : GuideFlowListener {
            override fun onAnchorMissing(flowKey: String, anchorKey: String) {
                missingAnchor = anchorKey
            }
        })
        GuideFlow.loadLocalFlows(
            listOf(flow(step("s1", 1, StepType.TOOLTIP, "no_such_anchor", "Fallback Step"))),
        )
        rule.setHostWithAnchors()

        startFlow()

        rule.onNodeWithTag(GuideFlowOverlayTags.MODAL).assertIsDisplayed()
        rule.onNodeWithText("Fallback Step").assertIsDisplayed()
        assertEquals("no_such_anchor", missingAnchor)
    }

    @Test
    fun modalOverlay_isShown_forModalStep() {
        GuideFlow.loadLocalFlows(listOf(flow(step("s1", 1, StepType.MODAL, null, "Modal Step"))))
        rule.setHostWithAnchors()

        startFlow()

        rule.onNodeWithTag(GuideFlowOverlayTags.MODAL).assertIsDisplayed()
        rule.onNodeWithText("Modal Step").assertIsDisplayed()
    }

    @Test
    fun next_advancesToSecondStep() {
        GuideFlow.loadLocalFlows(
            listOf(
                flow(
                    step("s1", 1, StepType.TOOLTIP, "anchor_a", "First Step"),
                    step("s2", 2, StepType.TOOLTIP, "anchor_b", "Second Step"),
                ),
            ),
        )
        rule.setHostWithAnchors()
        startFlow()
        rule.onNodeWithText("First Step").assertIsDisplayed()

        rule.onNodeWithTag(GuideFlowOverlayTags.NEXT).performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Second Step").assertIsDisplayed()
        rule.onNodeWithText("First Step").assertDoesNotExist()
    }

    @Test
    fun skip_closesFlow() {
        GuideFlow.loadLocalFlows(listOf(flow(step("s1", 1, StepType.TOOLTIP, "anchor_a", "Only Step"))))
        rule.setHostWithAnchors()
        startFlow()
        rule.onNodeWithTag(GuideFlowOverlayTags.TOOLTIP).assertIsDisplayed()

        rule.onNodeWithTag(GuideFlowOverlayTags.SKIP).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(GuideFlowOverlayTags.TOOLTIP).assertDoesNotExist()
        rule.onNodeWithText("Only Step").assertDoesNotExist()
    }

    @Test
    fun done_completesFlow() {
        var completed = false
        GuideFlow.setListener(object : GuideFlowListener {
            override fun onFlowCompleted(flowKey: String) { completed = true }
        })
        GuideFlow.loadLocalFlows(listOf(flow(step("s1", 1, StepType.MODAL, null, "Final Step"))))
        rule.setHostWithAnchors()
        startFlow()

        // On the last step the Next button reads "Done" and completes the flow.
        rule.onNodeWithTag(GuideFlowOverlayTags.NEXT).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(GuideFlowOverlayTags.MODAL).assertDoesNotExist()
        assertTrue(completed)
    }
}
