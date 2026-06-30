package com.example.guideflow

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Offline fallback: the hardcoded tour is used if remote config is empty
        // or the backend is unreachable.
        GuideFlow.loadLocalFlows(listOf(demoTour, tapDemo))
        GuideFlow.setListener(object : GuideFlowListener {
            override fun onFlowStarted(flowKey: String) { Log.d(TAG, "started $flowKey") }
            override fun onStepChanged(flowKey: String, stepIndex: Int) { Log.d(TAG, "step $stepIndex") }
            override fun onFlowCompleted(flowKey: String) { Log.d(TAG, "completed $flowKey") }
            override fun onFlowSkipped(flowKey: String) { Log.d(TAG, "skipped $flowKey") }
            override fun onAnchorMissing(flowKey: String, anchorKey: String) {
                Log.d(TAG, "anchor missing: $anchorKey")
            }
            override fun onError(error: GuideFlowError) { Log.w(TAG, "error: $error") }
        })

        // Phase 3: initialize remote config. Paste a real key from POST /api/projects
        // and set BASE_URL to your machine's LAN IP to serve the tour from the backend.
        GuideFlow.initialize(
            context = applicationContext,
            projectKey = PROJECT_KEY,
            config = GuideFlowConfig(baseUrl = BASE_URL, debugLogging = true),
        )
        GuideFlow.setUser("demo-user-001")
        lifecycleScope.launch {
            val result = GuideFlow.refreshConfig()
            Log.d(TAG, "refreshConfig: $result")
        }

        enableEdgeToEdge()
        setContent {
            GuideFlowTheme {
                // GuideFlowHost wraps the app once near the root.
                GuideFlowHost {
                    DemoScreen()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GuideFlowDemo"

        // Hosted backend (Cloud Run) — works on any network.
        private const val BASE_URL = "https://guideflow-backend-794711970205.me-west1.run.app"

        // Paste a real key returned by POST /api/projects to load the tour remotely.
        // Until then, refresh fails and the demo uses the hardcoded fallback above.
        private const val PROJECT_KEY = "gf_42dbf36e28c81890a6b2aba336bef328"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finance Demo") },
                actions = {
                    TextButton(
                        onClick= {},
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

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { GuideFlow.startFlow("demo_tour") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start Tutorial") }
            Button(
                onClick = { GuideFlow.startFlow("tap_demo") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test Tap-to-Advance") }
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

/**
 * Local-only flow (not in remote config) to test advance-on-tap: tapping the
 * highlighted "Add Budget" button both runs its own action (increments the
 * counter) and advances the flow — no Next button on that step.
 */
private val tapDemo = TutorialFlow(
    id = "flow_tap",
    flowKey = "tap_demo",
    name = "Tap Demo",
    status = FlowStatus.PUBLISHED,
    steps = listOf(
        TutorialStep(
            id = "t1",
            order = 1,
            type = StepType.TOOLTIP,
            anchorKey = "add_budget_button",
            title = "Tap to add a budget",
            body = "Tap the button itself — it adds a budget and moves the tour forward.",
            advanceOnTap = true,
        ),
        TutorialStep(
            id = "t2",
            order = 2,
            type = StepType.MODAL,
            anchorKey = null,
            title = "Nice!",
            body = "Your tap ran the button's action and advanced the tutorial. Tap Done to finish.",
        ),
    ),
)
