package com.guideflow.sdk.compose

import androidx.compose.ui.graphics.Color
import com.guideflow.shared.FlowTheme

internal val DEFAULT_ACCENT = Color(0xFF4F5BD5)

/** Parse the flow theme's hex accent ("#RRGGBB"), falling back to the SDK default. */
internal fun FlowTheme.accentColorOrDefault(): Color =
    accentColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: DEFAULT_ACCENT
