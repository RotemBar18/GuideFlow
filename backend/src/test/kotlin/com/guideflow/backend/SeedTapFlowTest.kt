package com.guideflow.backend

import com.google.firebase.auth.FirebaseAuth
import com.guideflow.backend.auth.FirebaseAuthProvider
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.StepType
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * One-off seeder: adds a published "Tap to Advance" flow to the owner's existing
 * project (the one that already has the demo_tour flow), so it shows up in the
 * demo app and is editable in the portal. Idempotent. Skipped without Firebase creds.
 *
 * Run once:
 *   GUIDEFLOW_FIREBASE_CREDENTIALS=".../guideflow_key.json" \
 *   ./gradlew :backend:test -PbackendOnly --no-daemon --tests "com.guideflow.backend.SeedTapFlowTest"
 */
class SeedTapFlowTest {

    @Test
    fun seedTapToAdvanceFlow() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth) // no creds -> skip

        val email = System.getProperty("seedEmail") ?: "rotem68907@gmail.com"
        val uid = FirebaseAuth.getInstance().getUserByEmail(email).uid

        val store = FirestoreStore()
        // Find the project that holds the demo app's existing flow.
        val project = store.listProjects(uid)
            .firstOrNull { p -> store.listFlows(p.projectId).any { it.flowKey == "demo_tour" } }
            ?: store.listProjects(uid).firstOrNull()
            ?: error("No project found for $email")

        // Idempotent: don't create a duplicate on re-run.
        val existing = store.listFlows(project.projectId).firstOrNull { it.flowKey == "tap_to_advance" }
        if (existing != null) {
            store.publishFlow(existing.flowId)
            println("[SEED] tap_to_advance already existed; re-published. flowId=${existing.flowId} project=${project.projectId}")
            return
        }

        val flow = store.createFlow(project.projectId, "tap_to_advance", "Tap to Advance")
        store.addStep(
            flow.flowId,
            CreateStepRequest(
                type = StepType.SPOTLIGHT,
                anchorKey = "add_budget_button",
                title = "Tap to add a budget",
                body = "Tap the button itself — it adds a budget and continues the tour.",
                advanceOnTap = true,
            ),
        )
        store.addStep(
            flow.flowId,
            CreateStepRequest(
                type = StepType.MODAL,
                anchorKey = null,
                title = "Nice!",
                body = "Your tap ran the button's action and advanced the tutorial. Tap Done to finish.",
            ),
        )
        store.publishFlow(flow.flowId)

        println("[SEED] created+published tap_to_advance flowId=${flow.flowId} project=${project.projectId} owner=$uid")
    }

    /** A cross-page tour: tap "Open Details" to advance and land on the Details page. */
    @Test
    fun seedTwoPageTour() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth)

        val email = System.getProperty("seedEmail") ?: "rotem68907@gmail.com"
        val uid = FirebaseAuth.getInstance().getUserByEmail(email).uid

        val store = FirestoreStore()
        val project = store.listProjects(uid)
            .firstOrNull { p -> store.listFlows(p.projectId).any { it.flowKey == "demo_tour" } }
            ?: store.listProjects(uid).firstOrNull()
            ?: error("No project found for $email")

        val existing = store.listFlows(project.projectId).firstOrNull { it.flowKey == "page_tour" }
        if (existing != null) {
            store.publishFlow(existing.flowId)
            println("[SEED] page_tour already existed; re-published. flowId=${existing.flowId}")
            return
        }

        val flow = store.createFlow(project.projectId, "page_tour", "Two-Page Tour")
        store.addStep(
            flow.flowId,
            CreateStepRequest(
                type = StepType.SPOTLIGHT,
                anchorKey = "open_details_button",
                title = "Open the details page",
                body = "Tap this button — it takes you to the next page, and the tour follows.",
                advanceOnTap = true,
            ),
        )
        store.addStep(
            flow.flowId,
            CreateStepRequest(
                type = StepType.TOOLTIP,
                anchorKey = "save_button",
                title = "You're on page two",
                body = "This Save button lives on the Details page. Tap Done to finish.",
            ),
        )
        store.publishFlow(flow.flowId)

        println("[SEED] created+published page_tour flowId=${flow.flowId} project=${project.projectId}")
    }
}
