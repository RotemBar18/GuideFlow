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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.shared.FlowTheme
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SWATCHES = listOf("#4F5BD5", "#0F9D58", "#D64545", "#B45309", "#7C3AED", "#11141B")

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Gf.primary)

@Composable
fun AppearanceScreen(
    api: PortalApi,
    flow: TutorialFlow,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val t = flow.theme
    var accent by remember { mutableStateOf(t.accentColor ?: "#4F5BD5") }
    var dim by remember { mutableFloatStateOf(t.dimOpacity) }
    var corner by remember { mutableFloatStateOf(t.cornerRadius.toFloat()) }
    var nextL by remember { mutableStateOf(t.nextLabel) }
    var skipL by remember { mutableStateOf(t.skipLabel) }
    var doneL by remember { mutableStateOf(t.doneLabel) }
    var showProgress by remember { mutableStateOf(t.showProgress) }
    var showSkip by remember { mutableStateOf(t.showSkip) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val accentColor = parseColor(accent)

    Scaffold(
        containerColor = Gf.surface,
        topBar = { DetailHeader(backLabel = flow.name, title = "Appearance", onBack = onBack) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                error?.let { Text(it, color = Gf.errorFg, fontSize = 12.sp); Spacer(Modifier.height(6.dp)) }
                Button(
                    onClick = {
                        saving = true; error = null
                        val theme = FlowTheme(
                            accentColor = accent.trim().ifBlank { null },
                            dimOpacity = dim,
                            cornerRadius = corner.roundToInt(),
                            nextLabel = nextL.ifBlank { "Next" },
                            skipLabel = skipL.ifBlank { "Skip" },
                            doneLabel = doneL.ifBlank { "Done" },
                            showProgress = showProgress,
                            showSkip = showSkip,
                        )
                        scope.launch {
                            runCatching { api.updateFlowTheme(flow.id, theme, getToken()) }
                                .onSuccess { onBack() }
                                .onFailure { error = it.message ?: "Failed to save"; saving = false }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gf.primary),
                ) { Text("Save appearance", fontWeight = FontWeight.SemiBold) }
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
            ThemedPreview(accentColor, dim, corner.roundToInt(), doneL, skipL, showProgress, showSkip)

            SectionLabel("Accent color")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SWATCHES.forEach { hex ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(hex))
                            .border(if (hex.equals(accent, true)) 3.dp else 0.dp, Gf.ink, RoundedCornerShape(8.dp))
                            .clickable { accent = hex },
                    )
                }
            }
            OutlinedTextField(
                value = accent, onValueChange = { accent = it }, singleLine = true,
                label = { Text("Hex") }, modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Dim opacity  ${(dim * 100).roundToInt()}%")
            Slider(value = dim, onValueChange = { dim = it }, valueRange = 0.2f..0.9f)

            SectionLabel("Corner radius  ${corner.roundToInt()}dp")
            Slider(value = corner, onValueChange = { corner = it }, valueRange = 0f..28f)

            SectionLabel("Button labels")
            OutlinedTextField(value = nextL, onValueChange = { nextL = it }, singleLine = true, label = { Text("Next") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = skipL, onValueChange = { skipL = it }, singleLine = true, label = { Text("Skip") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = doneL, onValueChange = { doneL = it }, singleLine = true, label = { Text("Done (last step)") }, modifier = Modifier.fillMaxWidth())

            ToggleRow("Show step progress", showProgress) { showProgress = it }
            ToggleRow("Show skip button", showSkip) { showSkip = it }
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
    accent: Color, dim: Float, corner: Int, doneLabel: String, skipLabel: String,
    showProgress: Boolean, showSkip: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF0F3)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Column(
            Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(corner.dp)).background(Color.White).padding(16.dp),
        ) {
            Text("Welcome", color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("This is how your tutorial looks.", color = Gf.textMuted, fontSize = 12.sp)
            if (showProgress) {
                Spacer(Modifier.height(8.dp))
                Text("Step 1 of 3", color = Gf.textFaint, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showSkip) Text(skipLabel, color = Gf.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 18.dp, vertical = 8.dp),
                ) { Text(doneLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Default) }
            }
        }
    }
}
