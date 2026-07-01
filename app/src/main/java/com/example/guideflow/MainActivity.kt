package com.example.guideflow

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guideflow.sdk.api.GuideFlow
import com.guideflow.sdk.api.GuideFlowConfig
import com.guideflow.sdk.compose.GuideFlowHost
import com.guideflow.sdk.compose.guideFlowAnchor
import kotlinx.coroutines.delay

/** Pulse: a small, good-looking music player that shows GuideFlow in a real app. */
private object Pulse {
    val bg = Color(0xFF0F0D16)
    val surface = Color(0xFF1B1726)
    val textPrimary = Color(0xFFF4F1FA)
    val textMuted = Color(0xFF9A92AC)
    val accent = Color(0xFFEC4899)

    val gradients = listOf(
        Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))),
        Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
        Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))),
        Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF14B8A6))),
    )
}

private data class Playlist(val title: String, val subtitle: String, val art: Brush)

private val featured = listOf(
    Playlist("Chill Vibes", "Wind down", Pulse.gradients[0]),
    Playlist("Focus Flow", "Deep work", Pulse.gradients[1]),
)
private val playlists = listOf(
    Playlist("Daily Mix", "Made for you", Pulse.gradients[2]),
    Playlist("Workout", "42 songs", Pulse.gradients[3]),
    Playlist("Throwback", "2000s hits", Pulse.gradients[0]),
    Playlist("Rainy Day", "Lo-fi beats", Pulse.gradients[1]),
)

private enum class Screen { Library, Player }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The only required SDK setup: initialize with the project key.
        GuideFlow.initialize(applicationContext, PROJECT_KEY, GuideFlowConfig(debugLogging = true))

        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences("pulse", Context.MODE_PRIVATE) }
            fun tour() {
                GuideFlow.startFlow(TOUR)
            }
            // Auto-run the onboarding tour on first launch.
            LaunchedEffect(Unit) {
                GuideFlow.refreshConfig()
                if (!prefs.getBoolean("toured", false)) {
                    prefs.edit().putBoolean("toured", true).apply()
                    delay(600)
                    tour()
                }
            }

            GuideFlowHost {
                var screen by remember { mutableStateOf(Screen.Library) }
                when (screen) {
                    Screen.Library -> LibraryScreen(
                        onOpenPlayer = { screen = Screen.Player },
                        onTour = { tour() },
                    )
                    Screen.Player -> PlayerScreen(onBack = { screen = Screen.Library })
                }
            }
        }
    }

    companion object {
        // Project key from the portal (the "Pulse Demo" project). baseUrl defaults to the hosted backend.
        private const val PROJECT_KEY = "gf_e44af65c82025541cf566151b4c57dd4"
        private const val TOUR = "pulse_onboarding"
    }
}

@Composable
private fun LibraryScreen(onOpenPlayer: () -> Unit, onTour: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Pulse.bg),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Header
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Good evening", color = Pulse.textPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("What do you want to hear?", color = Pulse.textMuted, fontSize = 13.sp)
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Pulse.surface).clickable { onTour() },
                    contentAlignment = Alignment.Center,
                ) { Text("?", color = Pulse.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }

            // Featured
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                featured.forEachIndexed { i, p ->
                    FeaturedCard(
                        playlist = p,
                        modifier = Modifier.weight(1f).then(if (i == 0) Modifier.guideFlowAnchor("pulse_featured") else Modifier),
                        onClick = onOpenPlayer,
                    )
                }
            }

            Text(
                "Your playlists",
                color = Pulse.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 10.dp),
            )
            playlists.forEachIndexed { i, p ->
                PlaylistRow(
                    playlist = p,
                    modifier = if (i == 0) Modifier.guideFlowAnchor("pulse_playlist") else Modifier,
                    onClick = onOpenPlayer,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        MiniPlayer(onClick = onOpenPlayer)
    }
}

@Composable
private fun FeaturedCard(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(playlist.art)
            .clickable { onClick() }
            .height(150.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("♪", color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp)
        Column {
            Text(playlist.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(playlist.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(playlist.art), contentAlignment = Alignment.Center) {
            Text("♪", color = Color.White, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = Pulse.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(playlist.subtitle, color = Pulse.textMuted, fontSize = 12.5.sp)
        }
        Text("▷", color = Pulse.textMuted, fontSize = 18.sp)
    }
}

@Composable
private fun MiniPlayer(onClick: () -> Unit) {
    var playing by remember { mutableStateOf(true) }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Pulse.surface)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Pulse.gradients[0]), contentAlignment = Alignment.Center) {
            Text("♪", color = Color.White, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Blinding Lights", color = Pulse.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("The Weeknd", color = Pulse.textMuted, fontSize = 12.sp)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Pulse.accent).clickable { playing = !playing },
            contentAlignment = Alignment.Center,
        ) { Text(if (playing) "❚❚" else "▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@Composable
private fun PlayerScreen(onBack: () -> Unit) {
    var playing by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Pulse.bg).statusBarsPadding().padding(horizontal = 24.dp),
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = Pulse.textPrimary, fontSize = 30.sp,
                modifier = Modifier.guideFlowAnchor("pulse_back").clickable { onBack() }.padding(end = 12.dp),
            )
            Text("Now Playing", color = Pulse.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "≡", color = Pulse.textPrimary, fontSize = 24.sp,
                modifier = Modifier.guideFlowAnchor("pulse_queue").clickable { }.padding(start = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        // Album art
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Pulse.gradients[0]),
            contentAlignment = Alignment.Center,
        ) { Text("♪", color = Color.White, fontSize = 72.sp) }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Blinding Lights", color = Pulse.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("The Weeknd", color = Pulse.textMuted, fontSize = 14.sp)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Pulse.surface).guideFlowAnchor("pulse_like").clickable { liked = !liked },
                contentAlignment = Alignment.Center,
            ) { Text(if (liked) "♥" else "♡", color = if (liked) Pulse.accent else Pulse.textPrimary, fontSize = 20.sp) }
        }

        Spacer(Modifier.height(22.dp))
        // Seek bar
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Pulse.surface)) {
            Box(Modifier.fillMaxWidth(0.4f).height(5.dp).clip(RoundedCornerShape(50)).background(Pulse.accent))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("1:22", color = Pulse.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("3:20", color = Pulse.textMuted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(24.dp))
        // Controls
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("🔀", color = Pulse.textMuted, fontSize = 20.sp)
            Text("⏮", color = Pulse.textPrimary, fontSize = 30.sp)
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Pulse.accent).guideFlowAnchor("pulse_play").clickable { playing = !playing },
                contentAlignment = Alignment.Center,
            ) { Text(if (playing) "❚❚" else "▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            Text("⏭", color = Pulse.textPrimary, fontSize = 30.sp)
            Text("🔁", color = Pulse.textMuted, fontSize = 20.sp)
        }
    }
}
