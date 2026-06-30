package com.guideflow.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.ErrorStateView
import com.guideflow.portal.ui.EmptyState
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.sdk.compose.guideFlowAnchor
import com.guideflow.shared.ProjectDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    api: PortalApi,
    userEmail: String?,
    getToken: suspend () -> String?,
    onSignOut: () -> Unit,
    onOpenProject: (ProjectDto) -> Unit,
    onStartTour: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val projects = remember { mutableStateListOf<ProjectDto>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var revealedKey by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectDto?>(null) }

    suspend fun reload() {
        loading = true; error = null
        runCatching { api.listProjects(getToken()) }
            .onSuccess { projects.clear(); projects.addAll(it) }
            .onFailure { error = it.message ?: "Couldn't load projects" }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = Gf.surface,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Gf.card).statusBarsPadding().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Projects", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { reload() } }) { Text("↻", fontSize = 18.sp, color = Gf.textSecondary) }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 20.sp, color = Gf.textSecondary) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Take a tour") }, onClick = { menuOpen = false; onStartTour() })
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                        }
                    }
                }
                if (userEmail != null) {
                    Text(userEmail, color = Gf.textMuted, fontSize = 12.5.sp)
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = Gf.primary, contentColor = Color.White, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.guideFlowAnchor("portal_new_project"),
            ) { Text("+  New project", fontWeight = FontWeight.SemiBold) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gf.primary) }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorStateView("Couldn't load projects", "Something went wrong reaching GuideFlow. Your work is safe.", onRetry = { scope.launch { reload() } })
                }
                projects.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("No projects yet", "A project represents one of your apps. Create one to get its SDK project key.", "Create project", { showCreate = true })
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(projects, key = { it.projectId }) { p ->
                        val tourAnchor = if (p.projectId == projects.firstOrNull()?.projectId) {
                            Modifier.guideFlowAnchor("portal_project_card")
                        } else {
                            Modifier
                        }
                        ProjectCard(p, modifier = tourAnchor, onClick = { onOpenProject(p) }, onDelete = { deleteTarget = p })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                showCreate = false
                scope.launch {
                    runCatching { api.createProject(name, getToken()) }
                        .onSuccess { revealedKey = it.projectKey; reload() }
                        .onFailure { error = it.message ?: "Failed to create project" }
                }
            },
        )
    }

    if (revealedKey != null) {
        ProjectKeySheet(
            key = revealedKey!!,
            sheetState = rememberModalBottomSheetState(),
            onDismiss = { revealedKey = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete project?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${target.name}\", all its flows, steps, and analytics will be permanently deleted. Apps using its project key will stop receiving config.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            runCatching { api.deleteProject(target.projectId, getToken()) }
                                .onSuccess { reload() }
                                .onFailure { error = it.message ?: "Failed to delete project" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gf.errorFg),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = Gf.textSecondary) } },
        )
    }
}

@Composable
private fun ProjectCard(project: ProjectDto, modifier: Modifier = Modifier, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    GfCard(modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box {
                    Text(
                        "⋮", color = Gf.textSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { menu = true }.padding(horizontal = 6.dp),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete", color = Gf.errorFg) }, onClick = { menu = false; onDelete() })
                    }
                }
                Text("›", color = Gf.textFaint, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "v${project.configVersion}",
                    color = Gf.textSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Gf.chipBg).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    if (project.configVersion == 0) "not published yet" else "config version",
                    color = Gf.textMuted, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Project name") }, singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gf.primary)) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Gf.textSecondary) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectKeySheet(
    key: String,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Gf.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Gf.publishedBg), contentAlignment = Alignment.Center) {
                    Text("✓", color = Gf.publishedFg, fontSize = 18.sp)
                }
                Text("Project created", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Paste this project key into your app's SDK setup. It's shown only once, so copy it now.",
                color = Gf.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gf.fieldBg)
                    .border(1.dp, Gf.borderStrong, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(key, color = Gf.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(onClick = { clipboard.setText(AnnotatedString(key)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Gf.primary), shape = RoundedCornerShape(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Gf.warnBg)
                .border(1.dp, Gf.warnBorder, RoundedCornerShape(10.dp)).padding(12.dp)) {
                Text("⚠ You won't be able to see this key again. Store it somewhere safe.",
                    color = Gf.warnText, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gf.ink)) { Text("Done", fontWeight = FontWeight.SemiBold) }
        }
    }
}
