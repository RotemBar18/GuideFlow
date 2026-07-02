package com.guideflow.sdk.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/** Custom title/body text colour, or null to follow the host/card default. */
internal fun FlowTheme.textColorOrNull(): Color? = textColor?.toColorOrNull()

// Defaults below match the portal's live preview exactly, so a flow with no custom
// colours renders the same in the app as in the editor (independent of the host theme).
private val DEFAULT_CARD_LIGHT = Color.White
private val DEFAULT_CARD_DARK = Color(0xFF1B1F27)
private val DEFAULT_TEXT_LIGHT = Color(0xFF11141B)
private val DEFAULT_TEXT_DARK = Color.White

/**
 * A border shade derived from the card background: a bit darker for light cards, a bit
 * lighter for dark ones, so a tooltip's edge is always visible regardless of the host screen.
 */
internal fun autoEdgeColor(bg: Color, strength: Float = 0.24f): Color =
    if (bg.luminance() > 0.5f) androidx.compose.ui.graphics.lerp(bg, Color.Black, strength)
    else androidx.compose.ui.graphics.lerp(bg, Color.White, strength)

/** Card background: the theme's colour, or the default (white / dark-navy by device mode). */
@Composable
internal fun FlowTheme.cardColorOrDefault(): Color =
    backgroundColorOrNull() ?: if (isSystemInDarkTheme()) DEFAULT_CARD_DARK else DEFAULT_CARD_LIGHT

/** Title/body text colour: the theme's colour, or the default (near-black / white by device mode). */
@Composable
internal fun FlowTheme.contentColorOrDefault(): Color =
    textColorOrNull() ?: if (isSystemInDarkTheme()) DEFAULT_TEXT_DARK else DEFAULT_TEXT_LIGHT
