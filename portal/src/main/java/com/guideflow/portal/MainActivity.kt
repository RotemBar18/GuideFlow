package com.guideflow.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.ErrorBanner
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.PortalTheme
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.api.GuideFlowConfig
import com.guideflow.sdk.compose.GuideFlowHost
import com.guideflow.sdk.compose.guideFlowAnchor
import com.guideflow.shared.ProjectDto
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The portal onboards authors with a GuideFlow tour of itself, loaded from the
        // real published config (flow "portal_tour", owned and edited by the portal user).
        GuideFlow.initialize(
            context = applicationContext,
            projectKey = PORTAL_PROJECT_KEY,
            config = GuideFlowConfig(baseUrl = BASE_URL, enableAnalytics = false, debugLogging = true),
        )

        enableEdgeToEdge()
        setContent {
            PortalTheme { PortalApp() }
        }
    }

    companion object {
        const val BASE_URL = "https://guideflow-backend-794711970205.me-west1.run.app"
        // Identifies which project's published config to load the portal tour from
        // (the "GuideFlow Console" project, owned and editable by the portal user).
        // Not a secret (it ships in the app), and not the tutorial content itself.
        const val PORTAL_PROJECT_KEY = "gf_01b869e1baa953a206467b803739ebdb"
        const val TOUR_FLOW_KEY = "portal_tour"
    }
}

private sealed interface Screen {
    data object Projects : Screen
    data class Flows(val project: ProjectDto) : Screen
    data class Steps(val project: ProjectDto, val flow: TutorialFlow) : Screen
    data class StepEdit(val project: ProjectDto, val flow: TutorialFlow, val step: TutorialStep?) : Screen
    data class Analytics(val project: ProjectDto, val flow: TutorialFlow) : Screen
    data class Appearance(val project: ProjectDto, val flow: TutorialFlow) : Screen
}

@Composable
private fun PortalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { PortalAuth() }
    val api = remember { PortalApi() }

    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    var signingIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val getToken: suspend () -> String? = { auth.idToken() }

    if (!signedIn) {
        LoginScreen(
            busy = signingIn,
            error = authError,
            onSignIn = {
                signingIn = true; authError = null
                scope.launch {
                    auth.signInWithGoogle(context)
                        .onSuccess { signedIn = true }
                        .onFailure { authError = it.message ?: "Sign-in failed" }
                    signingIn = false
                }
            },
        )
        return
    }

    // Load the published config and auto-run the tour the first time this user signs in.
    val prefs = remember { context.getSharedPreferences("guideflow_portal", android.content.Context.MODE_PRIVATE) }
    fun startTour() {
        scope.launch {
            GuideFlow.refreshConfig()
            GuideFlow.startFlow(MainActivity.TOUR_FLOW_KEY)
        }
    }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        GuideFlow.refreshConfig()
        val key = "tour_shown_${auth.currentUser?.uid ?: "anon"}"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).apply()
            kotlinx.coroutines.delay(700) // let the first screen lay out its anchors
            GuideFlow.startFlow(MainActivity.TOUR_FLOW_KEY)
        }
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }
    GuideFlowHost {
    when (val s = screen) {
        Screen.Projects -> ProjectsScreen(
            api = api,
            userEmail = auth.currentUser?.email,
            getToken = getToken,
            onSignOut = { auth.signOut(); signedIn = false },
            onOpenProject = { screen = Screen.Flows(it) },
            onStartTour = { startTour() },
        )
        is Screen.Flows -> FlowsScreen(
            api = api, project = s.project, getToken = getToken,
            onBack = { screen = Screen.Projects },
            onOpenFlow = { screen = Screen.Steps(s.project, it) },
        )
        is Screen.Steps -> StepsScreen(
            api = api, project = s.project, flow = s.flow, getToken = getToken,
            onBack = { screen = Screen.Flows(s.project) },
            onAddStep = { screen = Screen.StepEdit(s.project, s.flow, null) },
            onEditStep = { step -> screen = Screen.StepEdit(s.project, s.flow, step) },
            onAnalytics = { screen = Screen.Analytics(s.project, s.flow) },
            onAppearance = { screen = Screen.Appearance(s.project, s.flow) },
        )
        is Screen.StepEdit -> StepEditorScreen(
            api = api, flow = s.flow, existing = s.step, getToken = getToken,
            onClose = { screen = Screen.Steps(s.project, s.flow) },
        )
        is Screen.Analytics -> AnalyticsScreen(
            api = api, flow = s.flow, getToken = getToken,
            onBack = { screen = Screen.Steps(s.project, s.flow) },
        )
        is Screen.Appearance -> AppearanceScreen(
            api = api, flow = s.flow, getToken = getToken,
            onBack = { screen = Screen.Steps(s.project, s.flow) },
        )
    }
    }
}

@Composable
private fun LoginScreen(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFFCFCFD)).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The app's launcher icon, above the name.
            Image(
                painter = painterResource(R.drawable.guideflow_logo),
                contentDescription = "GuideFlow",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
            )

            Spacer(Modifier.height(26.dp))
            Text("GuideFlow", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Build in-app tutorials your users actually finish.",
                color = Gf.textMuted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))
            if (error != null) {
                ErrorBanner("Couldn't sign in. Check your connection and try again.", Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            if (busy) {
                CircularProgressIndicator(color = Gf.primary)
                Spacer(Modifier.height(16.dp))
                Text("Signing you in…", color = Gf.textMuted, fontSize = 13.sp)
            } else {
                OutlinedButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    GoogleGlyph()
                    Spacer(Modifier.size(12.dp))
                    Text(if (error != null) "Try again" else "Sign in with Google",
                        color = Gf.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text("By continuing you agree to the Terms & Privacy Policy.",
                    color = Gf.textFaint, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun GoogleGlyph() {
    // Simple four-colour mark (Compose-only, no asset).
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).border(2.dp, Color(0xFF4285F4), RoundedCornerShape(50))) {
        Box(Modifier.fillMaxSize().padding(0.dp)) {
            Box(Modifier.size(10.dp).align(Alignment.TopStart).background(Color(0xFFEA4335)))
            Box(Modifier.size(10.dp).align(Alignment.TopEnd).background(Color(0xFFFBBC05)))
            Box(Modifier.size(10.dp).align(Alignment.BottomStart).background(Color(0xFF34A853)))
            Box(Modifier.size(10.dp).align(Alignment.BottomEnd).background(Color(0xFF4285F4)))
        }
    }
}
