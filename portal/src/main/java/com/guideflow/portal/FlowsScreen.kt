package com.guideflow.portal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.EmptyState
import com.guideflow.portal.ui.ErrorStateView
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.portal.ui.StatusPill
import com.guideflow.shared.ProjectDto
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowsScreen(
    api: PortalApi,
    project: ProjectDto,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
    onOpenFlow: (TutorialFlow) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flows = remember { mutableStateListOf<TutorialFlow>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true; error = null
        runCatching { api.listFlows(project.projectId, getToken()) }
            .onSuccess { flows.clear(); flows.addAll(it) }
            .onFailure { error = it.message ?: "Couldn't load flows" }
        loading = false
    }
    LaunchedEffect(project.projectId) { reload() }

    Scaffold(
        containerColor = Gf.surface,
        topBar = { DetailHeader(backLabel = "Projects", title = project.name, onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = Gf.primary, contentColor = Color.White, shape = RoundedCornerShape(16.dp),
            ) { Text("+  New flow", fontWeight = FontWeight.SemiBold) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gf.primary) }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorStateView("Couldn't load flows", "Something went wrong reaching GuideFlow.", onRetry = { scope.launch { reload() } })
                }
                flows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("No flows yet", "A flow is one tutorial — an ordered list of steps. Create your first one.", "New flow", { showCreate = true })
                }
                else -> Column(Modifier.fillMaxSize()) {
                    SectionLabel("Flows · ${flows.size}", Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(flows, key = { it.id }) { flow -> FlowCard(flow) { onOpenFlow(flow) } }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateFlowDialog(
            onDismiss = { showCreate = false },
            onCreate = { key, name ->
                showCreate = false
                scope.launch {
                    runCatching { api.createFlow(project.projectId, key, name, getToken()) }
                        .onSuccess { reload() }
                        .onFailure { error = it.message ?: "Failed to create flow" }
                }
            },
        )
    }
}

@Composable
private fun FlowCard(flow: TutorialFlow, onClick: () -> Unit) {
    GfCard(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(flow.name, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                StatusPill(flow.status)
            }
            Spacer(Modifier.height(7.dp))
            Text(flow.flowKey, color = Gf.textMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("${flow.steps.size} step${if (flow.steps.size == 1) "" else "s"}", color = Gf.textFaint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CreateFlowDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New flow", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = key, onValueChange = { key = it.lowercase().replace(' ', '_') },
                    label = { Text("Flow key *") }, singleLine = true,
                    supportingText = { Text("Used in code: startFlow(\"$key\"). Lowercase, no spaces — can't change later.", fontSize = 11.sp) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Display name *") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (key.isNotBlank() && name.isNotBlank()) onCreate(key.trim(), name.trim()) },
                enabled = key.isNotBlank() && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gf.primary)) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Gf.textSecondary) } },
    )
}

/** Shared white header with a back affordance + breadcrumb + title. */
@Composable
fun DetailHeader(
    backLabel: String,
    title: String,
    onBack: () -> Unit,
    status: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(Gf.card).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Back to $backLabel", color = Gf.textMuted, fontSize = 12.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            status?.invoke()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Gf.textMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}
