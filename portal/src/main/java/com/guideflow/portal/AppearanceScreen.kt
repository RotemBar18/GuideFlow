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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SWATCHES = listOf("#4F5BD5", "#0F9D58", "#D64545", "#B45309", "#7C3AED", "#11141B")

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

    val accentColor = parseColor(cur.accentColor ?: "#4F5BD5")
    val buttonTextColor = parseColorOrNull(cur.buttonTextColor) ?: Color.White
    // Card + text always follow the device theme; the preview mirrors the variant being edited.
    val cardColor = if (editingDark) Color(0xFF1B1F27) else Color.White
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Light / Dark variant switcher
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp)) {
                listOf(false to "Light", true to "Dark").forEach { (isDark, label) ->
                    val sel = editingDark == isDark
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (sel) Gf.primary else Color.Transparent)
                            .clickable { editingDark = isDark }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) Color.White else Gf.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                }
            }
            Text(
                "Editing the ${if (editingDark) "DARK" else "LIGHT"} design, shown automatically based on the device theme. Card and text follow the device; you set the accent and button-text color.",
                color = Gf.textMuted, fontSize = 11.5.sp,
            )

            ThemedPreview(accentColor, buttonTextColor, cardColor, txtColor, cur.dimOpacity, cur.cornerRadius, cur.doneLabel, cur.skipLabel, cur.showProgress, cur.showSkip)

            SectionLabel("Accent color")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SWATCHES.forEach { hex ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(hex))
                            .border(if (hex.equals(cur.accentColor, true)) 3.dp else 0.dp, Gf.ink, RoundedCornerShape(8.dp))
                            .clickable { set(cur.copy(accentColor = hex)) },
                    )
                }
            }
            OutlinedTextField(
                value = cur.accentColor ?: "", onValueChange = { set(cur.copy(accentColor = it.ifBlank { null })) },
                singleLine = true, label = { Text("Accent hex") }, modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Dim opacity  ${(cur.dimOpacity * 100).roundToInt()}%")
            Slider(value = cur.dimOpacity, onValueChange = { set(cur.copy(dimOpacity = it)) }, valueRange = 0.2f..0.9f)

            SectionLabel("Corner radius  ${cur.cornerRadius}dp")
            Slider(value = cur.cornerRadius.toFloat(), onValueChange = { set(cur.copy(cornerRadius = it.roundToInt())) }, valueRange = 0f..28f)

            SectionLabel("Button text color")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#FFFFFF", "#11141B").forEach { hex ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(hex))
                            .border(if (hex.equals(cur.buttonTextColor, true)) 3.dp else 1.dp, Gf.borderStrong, RoundedCornerShape(8.dp))
                            .clickable { set(cur.copy(buttonTextColor = hex)) },
                    )
                }
            }
            OutlinedTextField(value = cur.buttonTextColor ?: "", onValueChange = { set(cur.copy(buttonTextColor = it.ifBlank { null })) }, singleLine = true, label = { Text("Button text hex (blank = white)") }, modifier = Modifier.fillMaxWidth())

            SectionLabel("Button labels")
            OutlinedTextField(value = cur.nextLabel, onValueChange = { set(cur.copy(nextLabel = it)) }, singleLine = true, label = { Text("Next") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cur.skipLabel, onValueChange = { set(cur.copy(skipLabel = it)) }, singleLine = true, label = { Text("Skip") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cur.doneLabel, onValueChange = { set(cur.copy(doneLabel = it)) }, singleLine = true, label = { Text("Done (last step)") }, modifier = Modifier.fillMaxWidth())

            ToggleRow("Show step progress", cur.showProgress) { set(cur.copy(showProgress = it)) }
            ToggleRow("Show skip button", cur.showSkip) { set(cur.copy(showSkip = it)) }
            Spacer(Modifier.height(8.dp))
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
    doneLabel: String, skipLabel: String, showProgress: Boolean, showSkip: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF0F3)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
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
                ) { Text(doneLabel, color = buttonTextColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }
    }
}
