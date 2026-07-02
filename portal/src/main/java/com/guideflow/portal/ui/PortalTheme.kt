package com.guideflow.portal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * GuideFlow portal design tokens (light + dark). Accessed as [Gf] inside composables,
 * so call sites stay `Gf.primary` while the values follow the device theme.
 * Values come from the approved visual spec (see docs).
 */
data class GfColors(
    val isDark: Boolean,
    // brand
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // status
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    // surfaces
    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
    val outline: Color,
    val outlineStrong: Color,
    val scrim: Color,
    // brand gradient stops (Brush.linearGradient)
    val gradient: List<Color>,
) {
    // ---- Back-compat aliases so existing screens keep compiling unchanged ----
    val ink get() = onSurface
    val card get() = surface
    val border get() = outline
    val borderStrong get() = outlineStrong
    val textPrimary get() = onSurface
    val textSecondary get() = onSurfaceMuted
    val textMuted get() = onSurfaceFaint
    val textFaint get() = onSurfaceFaint
    val fieldBg get() = surfaceContainer
    val chipBg get() = surfaceContainer

    // type chips
    val tooltip get() = onPrimaryContainer;   val tooltipBg get() = primaryContainer
    val spotlight get() = onWarningContainer;  val spotlightBg get() = warningContainer
    val modal get() = onSecondaryContainer;    val modalBg get() = secondaryContainer

    // status pills
    val publishedFg get() = onSuccessContainer; val publishedBg get() = successContainer
    val draftFg get() = onWarningContainer;     val draftBg get() = warningContainer

    // errors / warnings
    val errorFg get() = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    val errorBg get() = if (isDark) Color(0xFF4A1414) else Color(0xFFFEE2E2)
    val errorBorder get() = if (isDark) Color(0xFF6B2A2A) else Color(0xFFF5C2C2)
    val errorText get() = if (isDark) Color(0xFFFECACA) else Color(0xFF991B1B)
    val warnBg get() = warningContainer
    val warnBorder get() = if (isDark) Color(0xFF5C3B0A) else Color(0xFFF4DCA0)
    val warnText get() = onWarningContainer

    /** The signature indigo->violet brand gradient. */
    fun brush(): Brush = Brush.linearGradient(gradient)
}

private val LightGf = GfColors(
    isDark = false,
    primary = Color(0xFF4F5BD5), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E7FB), onPrimaryContainer = Color(0xFF282E7C),
    secondary = Color(0xFF7C3AED), secondaryContainer = Color(0xFFEDE4FE), onSecondaryContainer = Color(0xFF4B1E93),
    tertiary = Color(0xFFEC4899), tertiaryContainer = Color(0xFFFCE1EE), onTertiaryContainer = Color(0xFF9B2064),
    success = Color(0xFF16A34A), successContainer = Color(0xFFDCFCE7), onSuccessContainer = Color(0xFF166534),
    warning = Color(0xFFB45309), warningContainer = Color(0xFFFEF3C7), onWarningContainer = Color(0xFF7C3D0A),
    background = Color(0xFFF4F5FA), surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFEEF0F8), surfaceContainerHigh = Color(0xFFE7E9F4),
    onSurface = Color(0xFF11141B), onSurfaceMuted = Color(0xFF5B6172), onSurfaceFaint = Color(0xFF8A90A2),
    outline = Color(0xFFE4E7EF), outlineStrong = Color(0xFFD3D7E3),
    scrim = Color(0x8C11141B),
    gradient = listOf(Color(0xFF4F5BD5), Color(0xFF7C3AED), Color(0xFFA855F7)),
)

private val DarkGf = GfColors(
    isDark = true,
    primary = Color(0xFF9AA1F2), onPrimary = Color(0xFF1A1E52),
    primaryContainer = Color(0xFF2E357F), onPrimaryContainer = Color(0xFFDDE0FC),
    secondary = Color(0xFFC6A8FA), secondaryContainer = Color(0xFF3B1F73), onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFF9A8D0), tertiaryContainer = Color(0xFF6B2450), onTertiaryContainer = Color(0xFFFCE1EE),
    success = Color(0xFF4ADE80), successContainer = Color(0xFF14532D), onSuccessContainer = Color(0xFFBBF7D0),
    warning = Color(0xFFFBBF24), warningContainer = Color(0xFF4A2C06), onWarningContainer = Color(0xFFFDE68A),
    background = Color(0xFF0B0D13), surface = Color(0xFF14171F), surfaceDim = Color(0xFF0F1219),
    surfaceContainer = Color(0xFF1B1F2A), surfaceContainerHigh = Color(0xFF232836),
    onSurface = Color(0xFFE9EAF2), onSurfaceMuted = Color(0xFFA2A8BC), onSurfaceFaint = Color(0xFF6B7186),
    outline = Color(0xFF262B38), outlineStrong = Color(0xFF374052),
    scrim = Color(0x9E000000),
    gradient = listOf(Color(0xFF4F5BD5), Color(0xFF7C3AED), Color(0xFF9333EA)),
)

private val LocalGfColors = staticCompositionLocalOf { LightGf }

/** The active token set for the current theme. Use as `Gf.primary`, `Gf.surface`, etc. */
val Gf: GfColors
    @Composable @ReadOnlyComposable
    get() = LocalGfColors.current

@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val gf = if (dark) DarkGf else LightGf
    val scheme = if (dark) {
        darkColorScheme(
            primary = gf.primary, onPrimary = gf.onPrimary,
            primaryContainer = gf.primaryContainer, onPrimaryContainer = gf.onPrimaryContainer,
            secondary = gf.secondary, tertiary = gf.tertiary,
            background = gf.background, onBackground = gf.onSurface,
            surface = gf.surface, onSurface = gf.onSurface,
            surfaceVariant = gf.surfaceContainer, onSurfaceVariant = gf.onSurfaceMuted,
            outline = gf.outlineStrong, error = gf.errorFg,
        )
    } else {
        lightColorScheme(
            primary = gf.primary, onPrimary = gf.onPrimary,
            primaryContainer = gf.primaryContainer, onPrimaryContainer = gf.onPrimaryContainer,
            secondary = gf.secondary, tertiary = gf.tertiary,
            background = gf.background, onBackground = gf.onSurface,
            surface = gf.surface, onSurface = gf.onSurface,
            surfaceVariant = gf.surfaceContainer, onSurfaceVariant = gf.onSurfaceMuted,
            outline = gf.outlineStrong, error = gf.errorFg,
        )
    }
    CompositionLocalProvider(LocalGfColors provides gf) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
