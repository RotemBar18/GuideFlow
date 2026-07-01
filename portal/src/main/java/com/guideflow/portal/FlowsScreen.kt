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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.BlockingOverlay
import com.guideflow.portal.ui.EmptyState
import com.guideflow.portal.ui.GfDialog
import com.guideflow.portal.ui.ErrorStateView
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.portal.ui.StatusPill
import com.guideflow.sdk.compose.guideFlowAnchor
import com.guideflow.shared.CreateStepRequest
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
    var deleteTarget by remember { mutableStateOf<TutorialFlow?>(null) }
    var renameTarget by remember { mutableStateOf<TutorialFlow?>(null) }
    var duplicateTarget by remember { mutableStateOf<TutorialFlow?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true; error = null
        runCatching { api.listFlows(project.projectId, getToken()) }
            .onSuccess { flows.clear(); flows.addAll(it) }
            .onFailure { error = it.message ?: "Couldn't load flows" }
        loading = false
    }
    LaunchedEffect(project.projectId) { reload() }

    // Copy a flow (steps + both themes) as a new DRAFT, for example to make an RTL variant.
    fun uniqueKey(base: String): String {
        val keys = flows.map { it.flowKey }.toSet()
        if (base !in keys) return base
        var i = 2
        while ("${base}_$i" in keys) i++
        return "${base}_$i"
    }
    suspend fun duplicate(src: TutorialFlow, newKey: String, newName: String) {
        error = null
        runCatching {
            val copy = api.createFlow(project.projectId, newKey, newName, getToken())
            src.steps.sortedBy { it.order }.forEach { s ->
                api.addStep(copy.id, CreateStepRequest(s.type, s.anchorKey, s.title, s.body, s.order, s.advanceOnTap), getToken())
            }
            api.updateFlowThemes(copy.id, src.theme, src.themeDark, getToken())
        }.onSuccess { reload() }.onFailure { error = it.message ?: "Failed to duplicate flow" }
    }

    Scaffold(
        containerColor = Gf.surface,
        topBar = { DetailHeader(backLabel = "Projects", title = project.name, onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = Gf.primary, contentColor = Color.White, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.guideFlowAnchor("portal_new_flow"),
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
                    EmptyState("No flows yet", "A flow is one tutorial: an ordered list of steps. Create your first one.", "New flow", { showCreate = true })
                }
                else -> Column(Modifier.fillMaxSize()) {
                    SectionLabel("Flows · ${flows.size}", Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(flows, key = { it.id }) { flow ->
                            FlowCard(
                                flow = flow,
                                isFirst = flow.id == flows.firstOrNull()?.id,
                                onClick = { onOpenFlow(flow) },
                                onRename = { renameTarget = flow },
                                onDuplicate = { duplicateTarget = flow },
                                onDelete = { deleteTarget = flow },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        FlowFormDialog(
            title = "New flow",
            confirmText = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { key, name ->
                showCreate = false
                scope.launch {
                    runCatching { api.createFlow(project.projectId, key, name, getToken()) }
                        .onSuccess { reload() }
                        .onFailure { error = it.message ?: "Failed to create flow" }
                }
            },
        )
    }

    duplicateTarget?.let { src ->
        FlowFormDialog(
            title = "Duplicate flow",
            confirmText = "Duplicate",
            initialKey = uniqueKey("${src.flowKey}_copy"),
            initialName = "${src.name} (copy)",
            onDismiss = { duplicateTarget = null },
            onConfirm = { key, name ->
                duplicateTarget = null
                scope.launch { duplicate(src, key, name) }
            },
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        GfDialog(
            title = "Rename flow",
            confirmText = "Save",
            confirmEnabled = newName.isNotBlank(),
            onConfirm = {
                val name = newName.trim()
                renameTarget = null
                scope.launch {
                    runCatching { api.renameFlow(target.id, name, getToken()) }
                        .onSuccess { reload() }
                        .onFailure { error = it.message ?: "Failed to rename flow" }
                }
            },
            onDismiss = { renameTarget = null },
        ) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleteTarget?.let { target ->
        GfDialog(
            title = "Delete flow?",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                busy = true
                scope.launch {
                    runCatching { api.deleteFlow(target.id, getToken()) }
                        .onSuccess { reload() }
                        .onFailure { error = it.message ?: "Failed to delete flow" }
                    busy = false
                }
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text(
                "\"${target.name}\" and its ${target.steps.size} step${if (target.steps.size == 1) "" else "s"} will be permanently deleted.",
                color = Gf.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp,
            )
        }
    }

    if (busy) BlockingOverlay("Deleting flow...")
}

@Composable
private fun FlowCard(flow: TutorialFlow, isFirst: Boolean = false, onClick: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val cardAnchor = if (isFirst) Modifier.guideFlowAnchor("portal_flow_card") else Modifier
    GfCard(cardAnchor.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(flow.name, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                StatusPill(flow.status)
                Box {
                    Text(
                        "⋮", color = Gf.textSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = (if (isFirst) Modifier.guideFlowAnchor("portal_flow_menu") else Modifier)
                            .clickable { menu = true }.padding(start = 12.dp, end = 2.dp),
                    )
                    DropdownMenu(
                        expanded = menu, onDismissRequest = { menu = false },
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Gf.card),
                    ) {
                        DropdownMenuItem(text = { Text("Rename", color = Gf.ink) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Duplicate", color = Gf.ink) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete", color = Gf.errorFg) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(flow.flowKey, color = Gf.textMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("${flow.steps.size} step${if (flow.steps.size == 1) "" else "s"}", color = Gf.textFaint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FlowFormDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    initialKey: String = "",
    initialName: String = "",
) {
    var key by remember { mutableStateOf(initialKey) }
    var name by remember { mutableStateOf(initialName) }
    GfDialog(
        title = title,
        confirmText = confirmText,
        confirmEnabled = key.isNotBlank() && name.isNotBlank(),
        onConfirm = { if (key.isNotBlank() && name.isNotBlank()) onConfirm(key.trim(), name.trim()) },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Display name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it.lowercase().replace(' ', '_') },
            label = { Text("Flow key *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Used in code: startFlow(\"$key\"). Lowercase, no spaces. Can't change later.", fontSize = 11.sp) },
        )
    }
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
    backAnchorKey: String? = null,
) {
    Column(Modifier.fillMaxWidth().background(Gf.card).statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Gf.textSecondary, fontSize = 22.sp,
                modifier = (if (backAnchorKey != null) Modifier.guideFlowAnchor(backAnchorKey) else Modifier)
                    .clickable(onClickLabel = "Back to $backLabel") { onBack() }.padding(end = 10.dp, top = 2.dp, bottom = 2.dp))
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
