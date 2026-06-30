package com.guideflow.sdk.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.guideflow.sdk.flow.ActiveFlowState
import com.guideflow.shared.FlowTheme

internal val DEFAULT_ACCENT = Color(0xFF4F5BD5)

/** The flow's theme for the device's current mode: dark variant in dark mode, else light. */
@Composable
internal fun ActiveFlowState.activeTheme(): FlowTheme =
    if (isSystemInDarkTheme()) flow.themeDark else flow.theme

private fun String.toColorOrNull(): Color? =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()

/** Parse the flow theme's hex accent ("#RRGGBB"), falling back to the SDK default. */
internal fun FlowTheme.accentColorOrDefault(): Color = accentColor?.toColorOrNull() ?: DEFAULT_ACCENT

/** Text/content color on the accent button; defaults to white. */
internal fun FlowTheme.buttonTextColorOrDefault(): Color = buttonTextColor?.toColorOrNull() ?: Color.White

/** Custom card background, or null to follow the host light/dark theme. */
internal fun FlowTheme.backgroundColorOrNull(): Color? = backgroundColor?.toColorOrNull()

/** Map the theme's font-family name to a Compose [FontFamily]. */
internal fun FlowTheme.fontFamilyOrDefault(): FontFamily = when (fontFamily) {
    "SansSerif" -> FontFamily.SansSerif
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    else -> FontFamily.Default
}
