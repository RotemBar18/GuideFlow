package com.guideflow.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
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
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.GfDialog
import com.guideflow.portal.ui.GfGradientButton
import com.guideflow.portal.ui.GfSegmented
import com.guideflow.portal.ui.InfoBanner
import com.guideflow.sdk.compose.guideFlowAnchor
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.portal.ui.autoEdgeColor
import com.guideflow.portal.ui.typeBlurb
import com.guideflow.portal.ui.typeColors
import com.guideflow.portal.ui.typeNeedsAnchor
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.StepType
import com.guideflow.shared.progressText
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
    var advanceOnTap by remember { mutableStateOf(existing?.advanceOnTap ?: false) }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The flow passed via navigation can be stale; fetch the latest so the preview
    // uses the flow's actual theme.
    var themedFlow by remember { mutableStateOf(flow) }
    LaunchedEffect(flow.id) {
        runCatching { api.getFlow(flow.id, getToken()) }.onSuccess { themedFlow = it }
    }
    var previewDark by remember { mutableStateOf(false) }
    val theme = if (previewDark) themedFlow.themeDark else themedFlow.theme
    val previewCard = parseHexOrNull(theme.backgroundColor) ?: if (previewDark) Color(0xFF1B1F27) else Color.White
    val previewText = parseHexOrNull(theme.textColor) ?: if (previewDark) Color.White else Gf.ink
    // Real step position so the preview hides Back on step 1 and shows the Done label on the last step.
    val previewTotal = themedFlow.steps.size.coerceAtLeast(1)
    val previewIndex = (existing?.let { e -> themedFlow.steps.indexOfFirst { it.id == e.id } } ?: -1).coerceAtLeast(0)

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
                    api.addStep(flow.id, CreateStepRequest(type, anchorKey.trim().ifBlank { null }, title.trim(), body.trim(), advanceOnTap = needsAnchor && advanceOnTap), token)
                }
            } else {
                runCatching {
                    api.updateStep(existing.id, UpdateStepRequest(type, anchorKey.trim().ifBlank { null }, title.trim(), body.trim(), advanceOnTap = needsAnchor && advanceOnTap), token)
                }
            }
            result.onSuccess { onClose() }.onFailure { serverError = it.message ?: "Failed to save step" }
            busy = false
        }
    }

    Scaffold(
        containerColor = Gf.surface,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Gf.card).statusBarsPadding().padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClose).guideFlowAnchor("portal_step_close"),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 18.sp, color = Gf.textSecondary) }
                Text(if (existing == null) "New step" else "Edit step", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Gf.surfaceDim).navigationBarsPadding().padding(16.dp)) {
                serverError?.let { Text(it, color = Gf.errorFg, fontSize = 12.sp); Spacer(Modifier.height(8.dp)) }
                GfGradientButton(if (busy) "Saving…" else "Save step", onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned: type selector + live preview (stays visible while editing the fields).
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Type")
                Box(Modifier.guideFlowAnchor("portal_step_type")) {
                    GfSegmented(
                        options = StepType.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        selected = type.ordinal,
                        onSelect = { type = StepType.entries[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PREVIEW", color = Gf.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, modifier = Modifier.weight(1f))
                    GfSegmented(
                        options = listOf("Light", "Dark"),
                        selected = if (previewDark) 1 else 0,
                        onSelect = { previewDark = it == 1 },
                        subtle = true,
                        modifier = Modifier.width(150.dp),
                    )
                }
                Box(Modifier.guideFlowAnchor("portal_step_preview")) {
                    LivePreview(
                        type = type,
                        title = title.ifBlank { "Title" },
                        body = body,
                        theme = theme,
                        accent = parseHex(theme.accentColor ?: "#4F5BD5"),
                        buttonText = parseHexOrNull(theme.buttonTextColor) ?: Color.White,
                        cardColor = previewCard,
                        textColor = previewText,
                        stepIndex = previewIndex,
                        totalSteps = previewTotal,
                        advanceByTap = needsAnchor && advanceOnTap,
                    )
                }
            }

            // Scrollable grouped cards.
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoBanner(typeBlurb(type), fg = typeFg, bg = typeBg, modifier = Modifier.fillMaxWidth())

                if (needsAnchor) {
                    GroupCard("Anchor key", trailing = {
                        Text("REQUIRED", color = Gf.errorFg, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Gf.errorBg).padding(horizontal = 6.dp, vertical = 1.5.dp))
                    }) {
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
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Gf.surfaceContainer)) {
                                SectionLabel("Used in this flow", Modifier.padding(start = 11.dp, top = 9.dp))
                                knownAnchors.forEach { k ->
                                    Text(k, color = if (k == anchorKey) Gf.primary else Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { anchorKey = k }.padding(horizontal = 11.dp, vertical = 8.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Must match the label in your app's code:", color = Gf.textMuted, fontSize = 11.5.sp)
                        Text("Modifier.guideFlowAnchor(\"${anchorKey.ifBlank { "add_budget_button" }}\")", color = Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                GroupCard("Content") {
                    Text("Title *", color = Gf.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, isError = titleError, modifier = Modifier.fillMaxWidth())
                    if (titleError) { Spacer(Modifier.height(6.dp)); Text("⚠ Title can't be empty.", color = Gf.errorFg, fontSize = 11.5.sp) }
                    Spacer(Modifier.height(14.dp))
                    Text("Body", color = Gf.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth())
                    Text("${body.length} / 120", color = Gf.textFaint, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
                }

                if (needsAnchor) {
                    GroupCard("Behavior") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Advance when the user taps the element", color = Gf.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Hides the Next button. The tap also runs the element's own action (e.g. navigation).", color = Gf.textMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(checked = advanceOnTap, onCheckedChange = { advanceOnTap = it })
                        }
                    }
                }

                if (existing != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete step", color = Gf.errorFg, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (existing != null && confirmDelete) {
            GfDialog(
                title = "Delete step?",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        runCatching { api.deleteStep(existing.id, getToken()) }
                            .onSuccess { onClose() }
                            .onFailure { serverError = it.message ?: "Failed to delete"; busy = false }
                    }
                },
                onDismiss = { confirmDelete = false },
            ) {
                Text("\"${existing.title.ifBlank { "This step" }}\" will be permanently removed from the flow.", color = Gf.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** Grouped settings card (label header + optional trailing chip), matching the theme editor. */
@Composable
private fun GroupCard(
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    GfCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(label, Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF4F5BD5))

private fun parseHexOrNull(hex: String?): Color? =
    hex?.ifBlank { null }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

@Composable
private fun LivePreview(
    type: StepType, title: String, body: String, theme: FlowTheme,
    accent: Color, buttonText: Color, cardColor: Color, textColor: Color,
    stepIndex: Int, totalSteps: Int, advanceByTap: Boolean,
) {
    val isFirst = stepIndex <= 0
    val isLast = stepIndex >= totalSteps - 1
    val nextLabel = if (isLast) theme.doneLabel else theme.nextLabel
    // Tooltip has no dimmer, so its optional shadow/border apply only to that type.
    val tooltipShadow = type == StepType.TOOLTIP && theme.tooltipShadow
    val tooltipBorder = type == StepType.TOOLTIP && theme.tooltipBorder
    val card: @Composable () -> Unit = {
        PreviewCard(title, body, theme, accent, buttonText, cardColor, textColor, isFirst, nextLabel, stepIndex, totalSteps, advanceByTap, tooltipShadow, tooltipBorder)
    }
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).background(Gf.surfaceContainer), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalLayoutDirection provides if (theme.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            when (type) {
                StepType.MODAL -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.dimOpacity)), contentAlignment = Alignment.Center) { card() }
                StepType.SPOTLIGHT -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.dimOpacity))) {
                    // Highlighted cutout near the top; controls card pinned at the bottom (matches the SDK).
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(width = 120.dp, height = 44.dp)
                        .clip(RoundedCornerShape(theme.cornerRadius.dp)).background(Gf.surfaceContainer).border(6.dp, Color(0x33FFFFFF), RoundedCornerShape(theme.cornerRadius.dp)))
                    Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) { card() }
                }
                StepType.TOOLTIP -> Box(Modifier.fillMaxSize()) {
                    // Anchor indicator + the tooltip card just below it.
                    Box(Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 14.dp).size(width = 84.dp, height = 28.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Gf.surfaceContainerHigh).border(2.dp, accent, RoundedCornerShape(8.dp)))
                    Box(Modifier.align(Alignment.Center).padding(8.dp)) { card() }
                }
            }
        }
    }
}

/** Mirrors the SDK's StepControls so the preview matches the published overlay exactly. */
@Composable
private fun PreviewCard(
    title: String, body: String, theme: FlowTheme,
    accent: Color, buttonText: Color, cardColor: Color, textColor: Color,
    isFirst: Boolean, nextLabel: String, stepIndex: Int, totalSteps: Int, advanceByTap: Boolean,
    showShadow: Boolean = false, showBorder: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth(0.82f)
            .then(if (showShadow) Modifier.shadow(theme.tooltipShadowStrength.dp, RoundedCornerShape(theme.cornerRadius.dp)) else Modifier)
            .clip(RoundedCornerShape(theme.cornerRadius.dp))
            .background(cardColor)
            .then(if (showBorder) Modifier.border(1.dp, autoEdgeColor(cardColor, theme.tooltipBorderStrength), RoundedCornerShape(theme.cornerRadius.dp)) else Modifier)
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = textColor, fontWeight = FontWeight.Bold, fontSize = theme.titleSize.sp, modifier = Modifier.weight(1f))
            if (theme.showSkip && stepIndex < totalSteps - 1) Text(theme.skipLabel, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(body, color = textColor.copy(alpha = 0.7f), fontSize = theme.bodySize.sp, lineHeight = (theme.bodySize + 4).sp, maxLines = 3)
        }
        if (theme.showProgress) {
            Spacer(Modifier.height(8.dp))
            Text(theme.progressText(stepIndex + 1, totalSteps), color = textColor.copy(alpha = 0.55f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (theme.showBack && !isFirst) Text(theme.backLabel, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (!advanceByTap) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(accent).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(nextLabel, color = buttonText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
