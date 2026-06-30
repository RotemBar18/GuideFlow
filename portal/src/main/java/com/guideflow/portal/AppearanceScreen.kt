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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ACCENT_SWATCHES = listOf("#4F5BD5", "#0F9D58", "#D64545", "#B45309", "#7C3AED", "#11141B")

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Gf.primary)

private fun parseColorOrNull(hex: String?): Color? =
    hex?.ifBlank { null }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

@Composable
fun AppearanceScreen(
    api: PortalApi,
    flow: TutorialFlow,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var light by remember { mutableStateOf(flow.theme) }
    var dark by remember { mutableStateOf(flow.themeDark) }
    var editingDark by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cur = if (editingDark) dark else light
    fun set(t: FlowTheme) { if (editingDark) dark = t else light = t }
    // Layout direction is a flow property, not a colour-mode one — keep both variants in sync.
    fun setRtl(rtl: Boolean) { light = light.copy(rtl = rtl); dark = dark.copy(rtl = rtl) }

    val accentColor = parseColor(cur.accentColor ?: "#4F5BD5")
    val buttonTextColor = parseColorOrNull(cur.buttonTextColor) ?: Color.White
    val cardColor = parseColorOrNull(cur.backgroundColor) ?: if (editingDark) Color(0xFF1B1F27) else Color.White
    val txtColor = if (editingDark) Color.White else Gf.ink

    Scaffold(
        containerColor = Gf.surface,
        topBar = { DetailHeader(backLabel = flow.name, title = "Appearance", onBack = onBack) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                error?.let { Text(it, color = Gf.errorFg, fontSize = 12.sp); Spacer(Modifier.height(6.dp)) }
                Button(
                    onClick = {
                        saving = true; error = null
                        scope.launch {
                            runCatching { api.updateFlowThemes(flow.id, light, dark, getToken()) }
                                .onSuccess { onBack() }
                                .onFailure { error = it.message ?: "Failed to save"; saving = false }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gf.primary),
                ) { Text("Save light + dark", fontWeight = FontWeight.SemiBold) }
                Text(
                    "Saving moves the flow to Draft — republish to push the new look.",
                    color = Gf.textFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Pinned: variant switcher + live preview (stays visible while you scroll).
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TwoWaySegment("Light", "Dark", editingDark) { editingDark = it }
                ThemedPreview(accentColor, buttonTextColor, cardColor, txtColor, cur.dimOpacity, cur.cornerRadius, cur.nextLabel, cur.skipLabel, cur.showProgress, cur.showSkip, cur.rtl)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Colours below apply to the ${if (editingDark) "DARK" else "LIGHT"} design; the SDK picks one based on the device theme. Card and text always follow the device.",
                    color = Gf.textMuted, fontSize = 11.5.sp,
                )

                // ---- Layout (whole flow) ----
                SectionLabel("Layout direction")
                TwoWaySegment("LTR", "RTL", cur.rtl) { setRtl(it) }
                Text("Applies to both light and dark.", color = Gf.textFaint, fontSize = 11.sp)

                // ---- The Next/Done button ----
                SectionLabel("Button — Next / Done")
                Text(
                    "One button: it reads your Next label on every step and your Done label on the last step.",
                    color = Gf.textMuted, fontSize = 11.5.sp,
                )
                Text("Accent", color = Gf.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SwatchRow(ACCENT_SWATCHES, cur.accentColor) { set(cur.copy(accentColor = it)) }
                OutlinedTextField(cur.accentColor ?: "", { set(cur.copy(accentColor = it.ifBlank { null })) }, singleLine = true, label = { Text("Accent hex") }, modifier = Modifier.fillMaxWidth())
                Text("Button text", color = Gf.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SwatchRow(listOf("#FFFFFF", "#11141B"), cur.buttonTextColor) { set(cur.copy(buttonTextColor = it)) }
                OutlinedTextField(cur.buttonTextColor ?: "", { set(cur.copy(buttonTextColor = it.ifBlank { null })) }, singleLine = true, label = { Text("Button text hex (blank = white)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cur.nextLabel, { set(cur.copy(nextLabel = it)) }, singleLine = true, label = { Text("Next label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cur.doneLabel, { set(cur.copy(doneLabel = it)) }, singleLine = true, label = { Text("Done label (last step)") }, modifier = Modifier.fillMaxWidth())

                // ---- The Back button ----
                SectionLabel("Button — Back")
                Text("Shown on every step except the first.", color = Gf.textMuted, fontSize = 11.5.sp)
                OutlinedTextField(cur.backLabel, { set(cur.copy(backLabel = it)) }, singleLine = true, label = { Text("Back label") }, modifier = Modifier.fillMaxWidth())

                // ---- The Skip button ----
                SectionLabel("Button — Skip")
                ToggleRow("Show skip button", cur.showSkip) { set(cur.copy(showSkip = it)) }
                OutlinedTextField(cur.skipLabel, { set(cur.copy(skipLabel = it)) }, singleLine = true, label = { Text("Skip label") }, modifier = Modifier.fillMaxWidth())

                // ---- The card / overlay ----
                SectionLabel("Card & overlay")
                Text("Background (blank = follow device light/dark)", color = Gf.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SwatchRow(listOf("#FFFFFF", "#1B1F27"), cur.backgroundColor) { set(cur.copy(backgroundColor = it)) }
                OutlinedTextField(cur.backgroundColor ?: "", { set(cur.copy(backgroundColor = it.ifBlank { null })) }, singleLine = true, label = { Text("Card background hex") }, modifier = Modifier.fillMaxWidth())
                Text("Corner radius  ${cur.cornerRadius}dp", color = Gf.textSecondary, fontSize = 12.sp)
                Slider(value = cur.cornerRadius.toFloat(), onValueChange = { set(cur.copy(cornerRadius = it.roundToInt())) }, valueRange = 0f..28f)
                Text("Dim opacity  ${(cur.dimOpacity * 100).roundToInt()}%", color = Gf.textSecondary, fontSize = 12.sp)
                Slider(value = cur.dimOpacity, onValueChange = { set(cur.copy(dimOpacity = it)) }, valueRange = 0.2f..0.9f)

                // ---- Progress ----
                SectionLabel("Progress")
                ToggleRow("Show step count (Step 1 of N)", cur.showProgress) { set(cur.copy(showProgress = it)) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TwoWaySegment(left: String, right: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp)) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val sel = rightSelected == isRight
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) Gf.primary else Color.Transparent)
                    .clickable { onSelect(isRight) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) Color.White else Gf.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
        }
    }
}

@Composable
private fun SwatchRow(options: List<String>, selected: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { hex ->
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(hex))
                    .border(if (hex.equals(selected, true)) 3.dp else 1.dp, Gf.borderStrong, RoundedCornerShape(8.dp))
                    .clickable { onPick(hex) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Gf.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemedPreview(
    accent: Color, buttonTextColor: Color, cardColor: Color, textColor: Color, dim: Float, corner: Int,
    nextLabel: String, skipLabel: String, showProgress: Boolean, showSkip: Boolean, rtl: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF0F3)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(
                Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(corner.dp)).background(cardColor).padding(16.dp),
            ) {
                Text("Welcome", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("This is how your tutorial looks.", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                if (showProgress) {
                    Spacer(Modifier.height(8.dp))
                    Text("Step 1 of 3", color = textColor.copy(alpha = 0.55f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (showSkip) Text(skipLabel, color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 18.dp, vertical = 8.dp),
                    ) { Text(nextLabel, color = buttonTextColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
        }
    }
}
