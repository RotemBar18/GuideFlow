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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.InfoBanner
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.portal.ui.typeBlurb
import com.guideflow.portal.ui.typeColors
import com.guideflow.portal.ui.typeNeedsAnchor
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.StepType
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep
import com.guideflow.shared.UpdateStepRequest
import kotlinx.coroutines.launch

@Composable
fun StepEditorScreen(
    api: PortalApi,
    flow: TutorialFlow,
    existing: TutorialStep?,
    getToken: suspend () -> String?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: StepType.TOOLTIP) }
    var anchorKey by remember { mutableStateOf(existing?.anchorKey ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val knownAnchors = remember(flow) {
        flow.steps.mapNotNull { it.anchorKey }.filter { it.isNotBlank() }.distinct()
    }

    val needsAnchor = typeNeedsAnchor(type)
    val anchorError = tried && needsAnchor && anchorKey.isBlank()
    val titleError = tried && title.isBlank()
    val (typeFg, typeBg) = typeColors(type)

    fun save() {
        tried = true
        if (title.isBlank() || (needsAnchor && anchorKey.isBlank())) return
        busy = true; serverError = null
        scope.launch {
            val token = getToken()
            val result = if (existing == null) {
                runCatching {
                    api.addStep(flow.id, CreateStepRequest(type, anchorKey.trim().ifBlank { null }, title.trim(), body.trim()), token)
                }
            } else {
                runCatching {
                    api.updateStep(existing.id, UpdateStepRequest(type, anchorKey.trim().ifBlank { null }, title.trim(), body.trim()), token)
                }
            }
            result.onSuccess { onClose() }.onFailure { serverError = it.message ?: "Failed to save step" }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Gf.surface)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Gf.card).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("✕", fontSize = 18.sp, color = Gf.textSecondary) }
            Text(if (existing == null) "New step" else "Edit step", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Button(onClick = { save() }, enabled = !busy, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gf.primary)) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }

        // Pinned: type selector + live preview (stays visible while editing the fields).
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Type")
            TypeSegmented(type) { type = it }
            LivePreview(
                type = type,
                title = title.ifBlank { "Title" },
                body = body,
                accent = parseHex(flow.theme.accentColor ?: "#4F5BD5"),
                buttonText = parseHexOrNull(flow.theme.buttonTextColor) ?: Color.White,
                corner = flow.theme.cornerRadius,
                dim = flow.theme.dimOpacity,
                buttonLabel = flow.theme.nextLabel,
                rtl = flow.theme.rtl,
            )
        }

        // Scrollable fields.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            InfoBanner(typeBlurb(type), fg = typeFg, bg = typeBg, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            if (needsAnchor) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Anchor key")
                    Text("REQUIRED", color = Gf.errorFg, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Gf.errorBg).padding(horizontal = 6.dp, vertical = 1.5.dp))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = anchorKey, onValueChange = { anchorKey = it },
                    placeholder = { Text("e.g. add_budget_button", fontFamily = FontFamily.Monospace) },
                    singleLine = true, isError = anchorError, modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                )
                if (anchorError) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ ${if (type == StepType.SPOTLIGHT) "Spotlight" else "Tooltip"} steps must have an anchor key.", color = Gf.errorFg, fontSize = 11.5.sp)
                }
                if (knownAnchors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Gf.fieldBg).border(1.dp, Gf.borderStrong, RoundedCornerShape(9.dp))) {
                        SectionLabel("Used in this flow", Modifier.padding(start = 11.dp, top = 7.dp))
                        knownAnchors.forEach { k ->
                            Text(k, color = if (k == anchorKey) Gf.primary else Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clickable { anchorKey = k }.padding(horizontal = 11.dp, vertical = 7.dp))
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text("Must match the label in your app's code:", color = Gf.textMuted, fontSize = 11.5.sp)
                Text("Modifier.guideFlowAnchor(\"${anchorKey.ifBlank { "add_budget_button" }}\")", color = Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Gf.fieldBg)
                        .border(1.dp, Gf.borderStrong, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚓", color = Gf.textFaint, fontSize = 13.sp)
                    Text("Anchor key — not needed for modals.", color = Gf.textMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Title *")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, isError = titleError, modifier = Modifier.fillMaxWidth())
            if (titleError) {
                Spacer(Modifier.height(6.dp)); Text("⚠ Title can't be empty.", color = Gf.errorFg, fontSize = 11.5.sp)
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Body")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth())
            Text("${body.length} / 120", color = Gf.textFaint, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)

            serverError?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = Gf.errorFg, fontSize = 12.sp)
            }

            if (existing != null) {
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        runCatching { api.deleteStep(existing.id, getToken()) }
                            .onSuccess { onClose() }
                            .onFailure { serverError = it.message ?: "Failed to delete"; busy = false }
                    }
                }) { Text("Delete step", color = Gf.errorFg, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TypeSegmented(selected: StepType, onSelect: (StepType) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp)) {
        StepType.entries.forEach { t ->
            val sel = t == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) Gf.primary else Color.Transparent)
                    .clickable { onSelect(t) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (sel) Color.White else Gf.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                )
            }
        }
    }
}

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Gf.primary)

private fun parseHexOrNull(hex: String?): Color? =
    hex?.ifBlank { null }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

@Composable
private fun LivePreview(
    type: StepType, title: String, body: String,
    accent: Color, buttonText: Color, corner: Int, dim: Float, buttonLabel: String, rtl: Boolean,
) {
    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF0F3)), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            when (type) {
                StepType.MODAL -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)), contentAlignment = Alignment.Center) {
                    PreviewCard(title, body, true, accent, buttonText, corner, buttonLabel)
                }
                StepType.SPOTLIGHT -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 120.dp, height = 48.dp).clip(RoundedCornerShape(corner.dp)).background(Color(0xFFEEF0F3)).border(6.dp, Color(0x33FFFFFF), RoundedCornerShape(corner.dp)))
                        Spacer(Modifier.height(12.dp))
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (body.isNotBlank()) Text(body, color = Gf.textFaint, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
                StepType.TOOLTIP -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 24.dp).size(width = 84.dp, height = 30.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xFFCFD3DC)).border(2.dp, accent, RoundedCornerShape(8.dp)))
                    Box(Modifier.align(Alignment.Center).padding(8.dp)) { PreviewCard(title, body, false, accent, buttonText, corner, buttonLabel) }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(title: String, body: String, centered: Boolean, accent: Color, buttonText: Color, corner: Int, buttonLabel: String) {
    Column(
        Modifier.fillMaxWidth(0.74f).clip(RoundedCornerShape(corner.dp)).background(Color.White).padding(13.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(title, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(body, color = Gf.textMuted, fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 3, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(if (centered) 1f else 0.5f).clip(RoundedCornerShape(8.dp)).background(accent).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(buttonLabel, color = buttonText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
