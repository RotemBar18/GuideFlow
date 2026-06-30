package com.guideflow.sdk.compose

import androidx.compose.ui.graphics.Color
import com.guideflow.shared.FlowTheme

internal val DEFAULT_ACCENT = Color(0xFF4F5BD5)

private fun String.toColorOrNull(): Color? =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()

/** Parse the flow theme's hex accent ("#RRGGBB"), falling back to the SDK default. */
internal fun FlowTheme.accentColorOrDefault(): Color = accentColor?.toColorOrNull() ?: DEFAULT_ACCENT

/** Explicit card background, or null to follow the host app's light/dark theme. */
internal fun FlowTheme.backgroundColorOrNull(): Color? = backgroundColor?.toColorOrNull()

/** Explicit text color, or null to follow the host app's theme. */
internal fun FlowTheme.textColorOrNull(): Color? = textColor?.toColorOrNull()
