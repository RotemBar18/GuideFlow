package com.guideflow.backend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.guideflow.backend.auth.FirebaseAuthProvider
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.FlowTheme
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

    /** One-off: zero the analytics summary for the portal_tour flow (deletes the summary doc). */
    @Test
    fun resetPortalTourSummary() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth) // no creds -> skip
        val db = FirestoreClient.getFirestore()
        val flows = db.collection("flows").whereEqualTo("flowKey", "portal_tour").get().get()
        if (flows.isEmpty) { println("[RESET] no portal_tour flow found"); return }
        for (f in flows.documents) {
            db.collection("analyticsSummaries").document(f.id).delete().get()
            println("[RESET] cleared summary for portal_tour flowId=${f.id}")
        }
    }

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

    /**
     * Seeds the "portal_tour" flow that the portal plays to onboard a new author.
     * It tours the portal's own screens using advance-on-tap to navigate; Back is
     * hidden (showBack = false) because the flow changes screens. Re-runnable.
     */
    @Test
    fun seedPortalTour() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth)

        val email = System.getProperty("seedEmail") ?: "rotem68907@gmail.com"
        val uid = FirebaseAuth.getInstance().getUserByEmail(email).uid

        val store = FirestoreStore()
        // Dedicated project so the demo app's flow picker never sees the portal tour.
        val consoleName = "GuideFlow Console"
        val existing = store.listProjects(uid).firstOrNull { it.name == consoleName }
        val projectId: String
        val keyLine: String
        if (existing != null) {
            projectId = existing.projectId
            keyLine = "projectId=$projectId key=(unchanged; shown only at creation)"
        } else {
            val (rec, rawKey) = store.createProject(uid, consoleName)
            projectId = rec.projectId
            keyLine = "projectId=$projectId key=$rawKey"
        }
        java.io.File(System.getProperty("user.dir"), "portal_tour_seed.txt").writeText(keyLine)

        // Recreate so re-running updates the content.
        store.listFlows(projectId).firstOrNull { it.flowKey == "portal_tour" }?.let { store.deleteFlow(it.flowId) }

        val flow = store.createFlow(projectId, "portal_tour", "Portal walkthrough")
        val noBack = FlowTheme(showBack = false)
        store.updateFlow(flow.flowId, flowKey = null, name = null, theme = noBack, themeDark = noBack)

        // (type, anchorKey, title, body, advanceOnTap). advanceOnTap drives screen changes.
        val steps = listOf(
            // Projects
            Triple(StepType.MODAL, null as String?, Triple("Welcome to GuideFlow", "This guided tour was built with GuideFlow itself. It walks you through authoring a tutorial from start to finish.", false)),
            Triple(StepType.SPOTLIGHT, "portal_new_project", Triple("Projects", "Each app you onboard is a project. It gets a one-time SDK key you paste into your app's code.", false)),
            Triple(StepType.TOOLTIP, "portal_project_card", Triple("Open a project", "A project holds all of an app's tutorials. Tap one to open it.", true)),
            // Flows
            Triple(StepType.SPOTLIGHT, "portal_new_flow", Triple("Flows", "A flow is one tutorial: an ordered list of steps. A project can have many.", false)),
            Triple(StepType.TOOLTIP, "portal_flow_menu", Triple("Manage a flow", "This menu renames a flow, duplicates it (perfect for a translated or right-to-left copy), or deletes it.", false)),
            Triple(StepType.TOOLTIP, "portal_flow_card", Triple("Open a flow", "Tap a flow to edit its steps.", true)),
            // Steps + step editor
            Triple(StepType.MODAL, null, Triple("The steps list", "Each row here is one step, played in order. You can reorder, edit, or add. Let's open the step editor.", false)),
            Triple(StepType.TOOLTIP, "portal_add_step", Triple("Add a step", "Tap Add step to open the editor.", true)),
            Triple(StepType.SPOTLIGHT, "portal_step_type", Triple("Step type", "Pick how the step appears: a tooltip bubble, a spotlight cut-out, or a centered modal.", false)),
            Triple(StepType.SPOTLIGHT, "portal_step_preview", Triple("Live preview", "This preview matches exactly what users see, in the flow's theme. Toggle light and dark right above it.", false)),
            Triple(StepType.MODAL, null, Triple("Anchors and tap-to-advance", "Tooltips and spotlights point at an element by its anchor key. A step can also advance when the user taps that element, so the tour follows real actions.", false)),
            Triple(StepType.TOOLTIP, "portal_step_close", Triple("Close the editor", "Tap the X to return to the steps. Nothing is saved unless you press Save.", true)),
            // Appearance
            Triple(StepType.TOOLTIP, "portal_appearance", Triple("Theme this flow", "Tap Theme to open the appearance editor.", true)),
            Triple(StepType.SPOTLIGHT, "portal_appearance_lightdark", Triple("Light and dark", "Design a separate look for light and dark mode. The SDK picks the right one on each device.", false)),
            Triple(StepType.SPOTLIGHT, "portal_appearance_preview", Triple("Live theme preview", "Everything updates here instantly: accent colour, corner radius, right-to-left, button labels, and the step counter.", false)),
            Triple(StepType.SPOTLIGHT, "portal_appearance_accent", Triple("Brand colour", "Set the accent used on the action button. The editor scrolled here on its own to show a control below the fold.", false)),
            Triple(StepType.SPOTLIGHT, "portal_appearance_textsize", Triple("Text size", "Tune the title and body text size. The font itself follows the host app.", false)),
            Triple(StepType.MODAL, null, Triple("Per-flow theming", "Each flow carries its own theme, so a promo can look different from onboarding.", false)),
            Triple(StepType.TOOLTIP, "portal_appearance_back", Triple("Back to steps", "Tap back to return.", true)),
            // Analytics
            Triple(StepType.TOOLTIP, "portal_analytics", Triple("See analytics", "Tap Analytics to see how real users move through this flow.", true)),
            Triple(StepType.SPOTLIGHT, "portal_analytics_hero", Triple("Completion rate", "The headline number: of everyone who started, how many finished.", false)),
            Triple(StepType.SPOTLIGHT, "portal_analytics_tiles", Triple("Key metrics", "Started, completed, skipped, and anchor-missing counts, collected from real devices.", false)),
            Triple(StepType.MODAL, null, Triple("Per-step views", "Below, a chart shows how many users saw each step, by name, so you can spot where people drop off.", false)),
            Triple(StepType.TOOLTIP, "portal_analytics_back", Triple("Back to steps", "Tap back to return.", true)),
            // Publish + finish
            Triple(StepType.SPOTLIGHT, "portal_publish", Triple("Publish", "When you're happy, publish. Your app picks up the change on its next launch, with no app release.", false)),
            Triple(StepType.MODAL, null, Triple("That's GuideFlow", "You've seen projects, flows, the step editor, theming, and analytics. Now build your own tutorial.", false)),
        )
        steps.forEach { (type, anchor, content) ->
            val (title, body, advance) = content
            store.addStep(flow.flowId, CreateStepRequest(type = type, anchorKey = anchor, title = title, body = body, advanceOnTap = advance))
        }
        store.publishFlow(flow.flowId)

        println("[SEED] created+published portal_tour flowId=${flow.flowId} $keyLine")
    }

    /** Seeds the Pulse music-player demo project + its onboarding tour, themed to match the app. */
    @Test
    fun seedPulseTour() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth)

        val email = System.getProperty("seedEmail") ?: "rotem68907@gmail.com"
        val uid = FirebaseAuth.getInstance().getUserByEmail(email).uid

        val store = FirestoreStore()
        val name = "Pulse Demo"
        val existing = store.listProjects(uid).firstOrNull { it.name == name }
        val projectId: String
        val keyLine: String
        if (existing != null) {
            projectId = existing.projectId
            keyLine = "projectId=$projectId key=(unchanged; shown only at creation)"
        } else {
            val (rec, rawKey) = store.createProject(uid, name)
            projectId = rec.projectId
            keyLine = "projectId=$projectId key=$rawKey"
        }
        java.io.File(System.getProperty("user.dir"), "pulse_seed.txt").writeText(keyLine)

        store.listFlows(projectId).firstOrNull { it.flowKey == "pulse_onboarding" }?.let { store.deleteFlow(it.flowId) }

        val flow = store.createFlow(projectId, "pulse_onboarding", "Pulse onboarding")
        // Dark, pink-accented theme to match the Pulse UI; Back hidden (the tour changes screens).
        val theme = FlowTheme(
            accentColor = "#EC4899",
            buttonTextColor = "#FFFFFF",
            backgroundColor = "#211C2E",
            textColor = "#F4F1FA", // light text on the dark card
            cornerRadius = 18,
            dimOpacity = 0.72f,
            showBack = false,
        )
        store.updateFlow(flow.flowId, flowKey = null, name = null, theme = theme, themeDark = theme)

        val steps = listOf(
            Triple(StepType.MODAL, null as String?, Triple("Welcome to Pulse", "A 30-second tour of your new music player.", false)),
            Triple(StepType.SPOTLIGHT, "pulse_featured", Triple("Made for you", "Featured mixes, hand-picked for your mood.", false)),
            Triple(StepType.TOOLTIP, "pulse_playlist", Triple("Open a playlist", "Tap a playlist to start listening.", true)),
            Triple(StepType.SPOTLIGHT, "pulse_play", Triple("Play and pause", "Your main control lives right here.", false)),
            Triple(StepType.TOOLTIP, "pulse_like", Triple("Save favourites", "Tap the heart to keep a track.", false)),
            Triple(StepType.TOOLTIP, "pulse_queue", Triple("Up next", "Peek at and reorder what's coming up.", false)),
            Triple(StepType.TOOLTIP, "pulse_back", Triple("Back to your library", "Tap back to browse more.", true)),
            Triple(StepType.MODAL, null, Triple("Enjoy Pulse", "That's it. Press play and enjoy the music.", false)),
        )
        steps.forEach { (type, anchor, content) ->
            val (title, body, advance) = content
            store.addStep(flow.flowId, CreateStepRequest(type = type, anchorKey = anchor, title = title, body = body, advanceOnTap = advance))
        }
        store.publishFlow(flow.flowId)

        println("[SEED] created+published pulse_onboarding flowId=${flow.flowId} $keyLine")
    }
}
