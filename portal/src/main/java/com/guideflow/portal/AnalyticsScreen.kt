package com.guideflow.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.portal.ui.Gf
import com.guideflow.portal.ui.GfCard
import com.guideflow.portal.ui.SectionLabel
import com.guideflow.shared.AnalyticsSummary
import com.guideflow.shared.TutorialFlow

@Composable
fun AnalyticsScreen(
    api: PortalApi,
    flow: TutorialFlow,
    getToken: suspend () -> String?,
    onBack: () -> Unit,
) {
    var summary by remember { mutableStateOf<AnalyticsSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(flow.id) {
        loading = true; error = null
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
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Started", s.started.toString(), Modifier.weight(1f))
                            StatCard("Completed", s.completed.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Skipped", s.skipped.toString(), Modifier.weight(1f))
                            StatCard("Completion", "$rate%", Modifier.weight(1f))
                        }
                        StatCard("Anchor missing", s.anchorMissing.toString(), Modifier.fillMaxWidth())

                        SectionLabel("Step views", Modifier.padding(top = 4.dp))
                        if (s.stepViews.isEmpty()) {
                            Text("No step views yet.", color = Gf.textMuted, fontSize = 13.sp)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(s.stepViews.entries.sortedByDescending { it.value }, key = { it.key }) { (stepId, count) ->
                                    GfCard(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(stepId, color = Gf.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Text(count.toString(), color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GfCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Gf.textMuted, fontSize = 12.sp)
        }
    }
}
