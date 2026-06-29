package com.guideflow.backend

import com.google.firebase.auth.FirebaseAuth
import com.guideflow.backend.auth.FirebaseAuthProvider
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.StepType
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * One-off seeder: creates a "GuideFlow Demo" project OWNED BY a real portal user
 * (looked up by email) with a published `demo_tour` flow matching the demo app's
 * anchors. Skipped unless Firebase credentials are configured.
 *
 * Run once:
 *   GUIDEFLOW_FIREBASE_CREDENTIALS=".../guideflow_key.json" \
 *   ./gradlew :backend:test -PbackendOnly --no-daemon --tests "com.guideflow.backend.SeedTest"
 */
class SeedTest {

    @Test
    fun seedDemoProjectForOwner() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth) // no creds -> skip

        val email = System.getProperty("seedEmail") ?: "rotem68907@gmail.com"
        val uid = FirebaseAuth.getInstance().getUserByEmail(email).uid

        val store = FirestoreStore()
        val (project, rawKey) = store.createProject(uid, "GuideFlow Demo")
        val flow = store.createFlow(project.projectId, "demo_tour", "Demo Tour")
        store.addStep(flow.flowId, CreateStepRequest(StepType.SPOTLIGHT, anchorKey = "budget_button", title = "Budget Planner", body = "Tap here to manage your monthly budget."))
        store.addStep(flow.flowId, CreateStepRequest(StepType.TOOLTIP, anchorKey = "add_budget_button", title = "Add a Budget", body = "Use this to create a new budget entry."))
        store.addStep(flow.flowId, CreateStepRequest(StepType.TOOLTIP, anchorKey = "profile_button", title = "Your Profile", body = "Open your profile to update account details."))
        store.addStep(flow.flowId, CreateStepRequest(StepType.MODAL, anchorKey = null, title = "You're all set", body = "That's the tour. Tap Done to finish."))
        store.publishFlow(flow.flowId)

        println("[SEED] email=$email owner=$uid projectId=${project.projectId} projectKey=$rawKey")
    }
}
