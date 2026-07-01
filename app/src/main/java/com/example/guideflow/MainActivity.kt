package com.example.guideflow

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.guideflow.ui.theme.GuideFlowTheme
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.api.GuideFlowConfig
import com.guideflow.sdk.api.GuideFlowError
import com.guideflow.sdk.api.GuideFlowListener
import com.guideflow.sdk.compose.GuideFlowHost
import com.guideflow.sdk.compose.guideFlowAnchor
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // REQUIRED: initialize once. baseUrl defaults to the hosted backend, and this
        // also refreshes config in the background, so refreshConfig() is not needed here.
        GuideFlow.initialize(applicationContext, PROJECT_KEY, GuideFlowConfig(debugLogging = true))

        // OPTIONAL below. Local flows are an offline fallback used only for keys the
        // backend doesn't define; setUser and setListener are conveniences.
        GuideFlow.loadLocalFlows(listOf(demoTour))
        GuideFlow.setUser("demo-user-001")
        GuideFlow.setListener(object : GuideFlowListener {
            override fun onFlowStarted(flowKey: String) { Log.d(TAG, "started $flowKey") }
            override fun onStepChanged(flowKey: String, stepIndex: Int) { Log.d(TAG, "step $stepIndex") }
            override fun onFlowCompleted(flowKey: String) { Log.d(TAG, "completed $flowKey") }
            override fun onFlowSkipped(flowKey: String) { Log.d(TAG, "skipped $flowKey") }
            override fun onAnchorMissing(flowKey: String, anchorKey: String) { Log.d(TAG, "anchor missing: $anchorKey") }
            override fun onError(error: GuideFlowError) { Log.w(TAG, "error: $error") }
        })

        enableEdgeToEdge()
        setContent {
            GuideFlowTheme {
                // GuideFlowHost wraps the app once near the root, so overlays and
                // anchors keep working as we navigate between pages.
                GuideFlowHost {
                    var screen by remember { mutableStateOf(Screen.Home) }
                    when (screen) {
                        Screen.Home -> HomeScreen(onOpenDetails = { screen = Screen.Details })
                        Screen.Details -> DetailsScreen(onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "GuideFlowDemo"

        // Project key from the portal. baseUrl defaults to the hosted backend.
        private const val PROJECT_KEY = "gf_42dbf36e28c81890a6b2aba336bef328"
    }
}

enum class Screen { Home, Details }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenDetails: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finance Demo") },
                actions = {
                    TextButton(
                        onClick = {},
                        modifier = Modifier.guideFlowAnchor("profile_button"),
                    ) { Text("Profile") }
                    TextButton(
                        onClick = {},
                        modifier = Modifier.guideFlowAnchor("settings_button"),
                    ) { Text("Settings") }
                },
            )
        },
    ) { innerPadding ->
        var budgets by remember { mutableIntStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Welcome back 👋", style = MaterialTheme.typography.headlineSmall)

            Card(modifier = Modifier.fillMaxWidth().guideFlowAnchor("budget_button")) {
                Column(Modifier.padding(20.dp)) {
                    Text("Budget Planner", style = MaterialTheme.typography.titleMedium)
                    Text("Track your monthly spending")
                }
            }

            // The button's own action runs on tap; an advance-on-tap step rides the same tap.
            Button(
                onClick = { budgets++ },
                modifier = Modifier.guideFlowAnchor("add_budget_button"),
            ) { Text("Add Budget") }
            Text("Budgets added: $budgets", style = MaterialTheme.typography.bodyMedium)

            // Navigates to the second page. A tutorial step can ride this tap
            // (advance-on-tap) so the next step continues on the Details page.
            Button(
                onClick = onOpenDetails,
                modifier = Modifier.fillMaxWidth().guideFlowAnchor("open_details_button"),
            ) { Text("Open Details") }

            Spacer(Modifier.weight(1f))

            // One button per published flow from the portal (plus any local fallback).
            var flows by remember { mutableStateOf(GuideFlow.availableFlows()) }
            LaunchedEffect(Unit) {
                GuideFlow.refreshConfig()
                flows = GuideFlow.availableFlows()
            }
            Text("Tutorials", style = MaterialTheme.typography.titleSmall)
            if (flows.isEmpty()) {
                Text("No published tutorials yet.", style = MaterialTheme.typography.bodySmall)
            }
            flows.forEach { flow ->
                Button(
                    onClick = { GuideFlow.startFlow(flow.flowKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(flow.name) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Account details", style = MaterialTheme.typography.headlineSmall)

            Card(modifier = Modifier.fillMaxWidth().guideFlowAnchor("details_card")) {
                Column(Modifier.padding(20.dp)) {
                    Text("Monthly summary", style = MaterialTheme.typography.titleMedium)
                    Text("Your spending breakdown for this month.")
                }
            }

            Button(
                onClick = {},
                modifier = Modifier.guideFlowAnchor("save_button"),
            ) { Text("Save changes") }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Back to Home") }
        }
    }
}

/**
 * Hardcoded tour demonstrating all overlay types and navigation:
 * spotlight, tooltip, the modal fallback (step 4 points at an unregistered
 * anchor), and a final modal step.
 */
private val demoTour = TutorialFlow(
    id = "flow_demo",
    flowKey = "demo_tour",
    name = "Demo Tour",
    status = FlowStatus.PUBLISHED,
    steps = listOf(
        TutorialStep(
            id = "s1",
            order = 1,
            type = StepType.SPOTLIGHT,
            anchorKey = "budget_button",
            title = "Budget Planner",
            body = "Tap here to manage your monthly budget.",
        ),
        TutorialStep(
            id = "s2",
            order = 2,
            type = StepType.TOOLTIP,
            anchorKey = "add_budget_button",
            title = "Add a Budget",
            body = "Use this button to create a new budget entry.",
        ),
        TutorialStep(
            id = "s3",
            order = 3,
            type = StepType.TOOLTIP,
            anchorKey = "profile_button",
            title = "Your Profile",
            body = "Open your profile to update account details.",
        ),
        TutorialStep(
            id = "s4",
            order = 4,
            type = StepType.SPOTLIGHT,
            anchorKey = "missing_demo_anchor",
            title = "Missing Anchor",
            body = "This step points at an element that isn't on screen, so GuideFlow shows a modal fallback.",
        ),
        TutorialStep(
            id = "s5",
            order = 5,
            type = StepType.MODAL,
            anchorKey = null,
            title = "You're all set!",
            body = "That's the end of the tour. Tap Done to finish.",
        ),
    ),
)
