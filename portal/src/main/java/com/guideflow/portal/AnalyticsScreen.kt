package com.guideflow.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.portal.ui.TypeBadge
import com.guideflow.portal.ui.typeColors
import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.TutorialFlow
import com.guideflow.shared.TutorialStep

@Composable
fun AnalyticsScreen(
    api: PortalApi,
    flow: TutorialFlow,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
) {
    var summary by remember { mutableStateOf<AnalyticsSummary?>(null) }
    // Step metadata so views show names, order and type instead of raw IDs.
    var steps by remember { mutableStateOf(flow.steps) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(flow.id) {
        loading = true; error = null
        runCatching { api.getFlow(flow.id, getToken()) }.onSuccess { steps = it.steps }
        runCatching { api.getAnalytics(flow.id, getToken()) }
            .onSuccess { summary = it }
            .onFailure { error = it.message ?: "Couldn't load analytics" }
        loading = false
    }

    Scaffold(
        containerColor = Gf.surface,
        topBar = { DetailHeader(backLabel = flow.name, title = "Analytics", onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gf.primary) }
                error != null -> Text(error!!, color = Gf.errorFg, modifier = Modifier.padding(16.dp))
                else -> {
                    val s = summary ?: AnalyticsSummary(flow.id)
                    val rate = if (s.started > 0) s.completed * 100 / s.started else 0
                    val stepById = remember(steps) { steps.associateBy { it.id } }
                    val rows = s.stepViews.entries.sortedByDescending { it.value }
                    val maxViews = s.stepViews.values.maxOrNull() ?: 1

                    LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { CompletionHero(rate = rate, completed = s.completed, started = s.started) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile("Started", s.started, Gf.tooltip, Gf.tooltipBg, Modifier.weight(1f))
                                MetricTile("Completed", s.completed, Gf.publishedFg, Gf.publishedBg, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile("Skipped", s.skipped, Gf.draftFg, Gf.draftBg, Modifier.weight(1f))
                                MetricTile("Anchor missing", s.anchorMissing, Gf.errorFg, Gf.errorBg, Modifier.weight(1f))
                            }
                        }
                        item { SectionLabel("Step views", Modifier.padding(top = 4.dp)) }

                        if (rows.isEmpty()) {
                            item { Text("No step views yet.", color = Gf.textMuted, fontSize = 13.sp) }
                        } else {
                            items(rows, key = { it.key }) { (stepId, count) ->
                                StepViewRow(step = stepById[stepId], fallbackId = stepId, count = count, maxViews = maxViews)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionHero(rate: Int, completed: Int, started: Int) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Gf.primary, Gf.modal))),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("COMPLETION RATE", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(6.dp))
            Text("$rate%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(rate / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color.White))
            }
            Spacer(Modifier.height(10.dp))
            Text("$completed of $started users finished this tutorial", color = Color.White.copy(alpha = 0.9f), fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: Int, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    GfCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(fg))
            Spacer(Modifier.height(10.dp))
            Text(value.toString(), color = fg, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = Gf.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StepViewRow(step: TutorialStep?, fallbackId: String, count: Int, maxViews: Int) {
    val (fg, bg) = step?.let { typeColors(it.type) } ?: (Gf.textMuted to Gf.chipBg)
    GfCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text(step?.order?.toString() ?: "•", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (step != null) {
                        Text(step.title.ifBlank { "Untitled step" }, color = Gf.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    } else {
                        Text(fallbackId, color = Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
                    }
                }
                step?.let {
                    TypeBadge(it.type)
                    Spacer(Modifier.width(8.dp))
                }
                Text(count.toString(), color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Gf.fieldBg)) {
                Box(Modifier.fillMaxWidth((count.toFloat() / maxViews).coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(fg))
            }
        }
    }
}
