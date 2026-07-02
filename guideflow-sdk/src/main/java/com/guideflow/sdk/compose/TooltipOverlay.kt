package com.guideflow.sdk.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.guideflow.sdk.anchor.AnchorInfo
import com.guideflow.sdk.flow.ActiveFlowState
import kotlin.math.roundToInt

/**
 * Highlights the anchor with an accent ring and floats a card just below it
 * (or above, when there isn't room below). The card is aligned horizontally to
 * the anchor and clamped to stay on screen. Unlike spotlight/modal it does not
 * dim the rest of the screen.
 */
@Composable
internal fun TooltipOverlay(state: ActiveFlowState, anchor: AnchorInfo) {
    val theme = state.activeTheme()
    val accent = theme.accentColorOrDefault()
    // Block the host UI; on advance-on-tap steps leave the anchor open so it stays tappable.
    if (state.currentStep.advanceOnTap) HoleScrim(anchor.bounds)
    else Box(Modifier.fillMaxSize().consumeTaps())
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val ringPadPx = with(density) { 6.dp.toPx() }
        val gapPx = with(density) { 14.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        // Accent highlight ring around the anchored element.
        val ringLeft = (anchor.bounds.left - ringPadPx)
        val ringTop = (anchor.bounds.top - ringPadPx)
        Box(
            Modifier
                .offset { IntOffset(ringLeft.roundToInt(), ringTop.roundToInt()) }
                .size(
                    width = with(density) { (anchor.bounds.width + ringPadPx * 2).toDp() },
                    height = with(density) { (anchor.bounds.height + ringPadPx * 2).toDp() },
                )
                .border(2.dp, accent, RoundedCornerShape(theme.cornerRadius.dp)),
        )

        // Measure the card so we can place it above when there's no room below.
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val belowY = anchor.bounds.bottom + gapPx
        val fitsBelow = belowY + cardSize.height <= maxHeightPx - marginPx
        val yPx = if (fitsBelow) belowY else (anchor.bounds.top - gapPx - cardSize.height).coerceAtLeast(marginPx)
        val xPx = anchor.bounds.left.coerceIn(marginPx, (maxWidthPx - cardSize.width - marginPx).coerceAtLeast(marginPx))

        val cardBg = theme.cardColorOrDefault()
        Card(
            modifier = Modifier
                .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                .widthIn(max = 320.dp)
                .onSizeChanged { cardSize = it }
                .testTag(GuideFlowOverlayTags.TOOLTIP),
            shape = RoundedCornerShape(theme.cornerRadius.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            // Tooltip has no dimmer: an optional shadow + auto-shaded border keep it readable.
            elevation = CardDefaults.cardElevation(defaultElevation = if (theme.tooltipShadow) theme.tooltipShadowStrength.dp else 0.dp),
            border = if (theme.tooltipBorder) BorderStroke(1.dp, autoEdgeColor(cardBg, theme.tooltipBorderStrength)) else null,
        ) {
            StepControls(state, Modifier.padding(16.dp), advanceByTap = state.currentStep.advanceOnTap)
        }
    }
}
