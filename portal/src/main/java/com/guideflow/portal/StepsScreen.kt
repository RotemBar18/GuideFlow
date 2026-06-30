package com.guideflow.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.EmptyState
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.StatusPill
import com.guideflow.portal.ui.TypeBadge
import com.guideflow.portal.ui.TypeGlyph
import com.guideflow.portal.ui.typeNeedsAnchor
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.ProjectDto
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsScreen(
    api: PortalApi,
    project: ProjectDto,
    flow: TutorialFlow,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (TutorialStep) -> Unit,
    onAnalytics: () -> Unit,
    onAppearance: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(flow) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var publishing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun reload() {
        loading = true
        runCatching { api.getFlow(flow.id, getToken()) }
            .onSuccess { current = it; error = null }
            .onFailure { error = it.message ?: "Couldn't load steps" }
        loading = false
    }
    LaunchedEffect(flow.id) { reload() }

    val steps = current.steps.sortedBy { it.order }
    val offending = steps.filter { typeNeedsAnchor(it.type) && it.anchorKey.isNullOrBlank() }.map { it.id }.toSet()
    val issues = buildList {
        steps.forEachIndexed { i, s ->
            if (typeNeedsAnchor(s.type) && s.anchorKey.isNullOrBlank()) {
                add("Step ${i + 1} (${s.type.name.lowercase().replaceFirstChar { it.uppercase() }}) needs an anchor key.")
            }
        }
        val orders = steps.map { it.order }
        if (orders.size != orders.toSet().size) add("Some steps share the same order number.")
    }
    val canPublish = steps.isNotEmpty() && issues.isEmpty()

    Scaffold(
        containerColor = Gf.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            DetailHeader(
                backLabel = project.name,
                title = current.name,
                onBack = onBack,
                status = { StatusPill(current.status) },
                subtitle = if (current.status == FlowStatus.DRAFT) "Draft - publish to make it live." else "Live.",
                action = {
                    Row {
                        TextButton(onClick = onAppearance) { Text("Theme") }
                        TextButton(onClick = onAnalytics) { Text("Analytics") }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onAddStep, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("+  Add step", color = Gf.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            publishing = true
                            scope.launch {
                                runCatching { api.publishFlow(current.id, getToken()) }
                                    .onSuccess { current = it; snackbar.showSnackbar("Flow published ✓") }
                                    .onFailure { snackbar.showSnackbar(it.message ?: "Publish failed") }
                                publishing = false
                            }
                        },
                        enabled = canPublish && !publishing,
                        modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gf.primary),
                    ) { Text("Publish", fontWeight = FontWeight.SemiBold) }
                }
                if (steps.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Add at least one step to publish.", color = Gf.textFaint, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gf.primary) }
                steps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Add your first step", "Each step is one screen of the tutorial: a tooltip, spotlight, or modal. Steps play in order.", "Add step", onAddStep)
                }
                else -> Column(Modifier.fillMaxSize()) {
                    if (issues.isNotEmpty()) {
                        ValidationBanner(issues, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                    }
                    error?.let { Text(it, color = Gf.errorFg, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(steps, key = { it.id }) { step ->
                            StepRow(step, order = steps.indexOf(step) + 1, hasError = step.id in offending) { onEditStep(step) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationBanner(issues: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gf.errorBg)
            .border(1.dp, Gf.errorBorder, RoundedCornerShape(12.dp)).padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(18.dp).height(18.dp).clip(RoundedCornerShape(50)).background(Gf.errorFg), contentAlignment = Alignment.Center) {
                Text("!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Can't publish yet: ${issues.size} issue${if (issues.size == 1) "" else "s"}", color = Gf.errorText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        issues.forEach { Text("•  $it", color = Color(0xFFA85151), fontSize = 12.sp, lineHeight = 19.sp) }
    }
}

@Composable
private fun StepRow(step: TutorialStep, order: Int, hasError: Boolean, onClick: () -> Unit) {
    GfCard(
        Modifier.fillMaxWidth().clickable { onClick() },
        borderColor = if (hasError) Gf.errorFg else Gf.border,
        borderWidth = if (hasError) 1.5.dp else 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            TypeGlyph(step.type)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeBadge(step.type)
                    Text(step.title, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                when {
                    hasError -> Text("⚠ Anchor key required", color = Gf.errorFg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    step.anchorKey != null -> Text("⚓ ${step.anchorKey}", color = Gf.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    else -> Text("no anchor needed", color = Gf.textFaint, fontSize = 11.sp)
                }
            }
            Text("$order", color = Gf.textFaint, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}
