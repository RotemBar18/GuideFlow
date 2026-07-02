package com.guideflow.portal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import com.guideflow.shared.FlowStatus
import com.guideflow.shared.StepType

/** (foreground, background) accent pair for a step type. */
@Composable
@ReadOnlyComposable
fun typeColors(type: StepType): Pair<Color, Color> = when (type) {
    StepType.TOOLTIP -> Gf.tooltip to Gf.tooltipBg
    StepType.SPOTLIGHT -> Gf.spotlight to Gf.spotlightBg
    StepType.MODAL -> Gf.modal to Gf.modalBg
}

fun typeBlurb(type: StepType): String = when (type) {
    StepType.TOOLTIP -> "A small bubble that points at one on-screen element. Needs an anchor key."
    StepType.SPOTLIGHT -> "Dims the screen and highlights one element with a cut-out. Needs an anchor key."
    StepType.MODAL -> "A centered dialog, not attached to anything. No anchor key needed."
}

fun typeNeedsAnchor(type: StepType): Boolean =
    type == StepType.TOOLTIP || type == StepType.SPOTLIGHT

/** Border shade for a tooltip card: darker on light backgrounds, lighter on dark ones. */
fun autoEdgeColor(bg: Color, strength: Float = 0.24f): Color =
    if (bg.luminance() > 0.5f) androidx.compose.ui.graphics.lerp(bg, Color.Black, strength)
    else androidx.compose.ui.graphics.lerp(bg, Color.White, strength)

/** Primary action, filled with the brand gradient. Falls back to a flat disabled state. */
@Composable
fun GfGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.background(Gf.brush()) else Modifier.background(Gf.surfaceContainerHigh))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Gf.onSurfaceFaint,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

/** Extended FAB with a leading plus, filled with the brand gradient. */
@Composable
fun GfFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Gf.brush())
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Segmented control. `subtle` uses a surface pill instead of the gradient for the selected tab. */
@Composable
fun GfSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtle: Boolean = false,
) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Gf.surfaceContainer).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        when {
                            on && !subtle -> Modifier.background(Gf.brush())
                            on -> Modifier.background(Gf.surface)
                            else -> Modifier
                        },
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when {
                        on && !subtle -> Color.White
                        on -> Gf.primary
                        else -> Gf.onSurfaceMuted
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Branded modal dialog: rounded card, bold title, custom content, and a Cancel +
 * primary/destructive action row. Replaces the plain Material AlertDialog.
 */
@Composable
fun GfDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Gf.card).padding(22.dp),
        ) {
            Text(title, color = Gf.ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Gf.textSecondary, fontWeight = FontWeight.SemiBold) }
                if (destructive) {
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gf.errorFg),
                    ) { Text(confirmText, fontWeight = FontWeight.SemiBold) }
                } else {
                    GfGradientButton(confirmText, onConfirm, enabled = confirmEnabled, height = 46.dp)
                }
            }
        }
    }
}

/** Full-screen scrim + spinner that blocks all input until an operation finishes. */
@Composable
fun BlockingOverlay(text: String = "Working...") {
    Box(
        Modifier
            .fillMaxSize()
            .background(Gf.scrim)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(14.dp))
            Text(text, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Gf.textSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    )
}

@Composable
fun Mono(text: String, color: Color = Gf.textSecondary, size: Int = 12) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp)
}

@Composable
fun StatusPill(status: FlowStatus, modifier: Modifier = Modifier) {
    val (fg, bg) = when (status) {
        FlowStatus.PUBLISHED -> Gf.publishedFg to Gf.publishedBg
        FlowStatus.DRAFT -> Gf.draftFg to Gf.draftBg
        FlowStatus.ARCHIVED -> Gf.textMuted to Gf.chipBg
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(status.name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.4.sp)
    }
}

@Composable
fun TypeBadge(type: StepType, modifier: Modifier = Modifier) {
    val (fg, bg) = typeColors(type)
    Text(
        type.name,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
    )
}

/** Small rounded tile with a schematic glyph for the step type. */
@Composable
fun TypeGlyph(type: StepType, modifier: Modifier = Modifier) {
    val (fg, bg) = typeColors(type)
    Box(
        modifier = modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        when (type) {
            StepType.TOOLTIP -> Box(Modifier.size(width = 16.dp, height = 11.dp).clip(RoundedCornerShape(3.dp)).background(fg))
            StepType.SPOTLIGHT -> Box(Modifier.size(15.dp).clip(RoundedCornerShape(50)).border(3.dp, fg, RoundedCornerShape(50)))
            StepType.MODAL -> Box(Modifier.size(width = 16.dp, height = 13.dp).clip(RoundedCornerShape(4.dp)).border(2.5.dp, fg, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun GfCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Gf.border,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Gf.card)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp)),
    ) { content() }
}

@Composable
fun InfoBanner(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("ⓘ", color = fg, fontSize = 13.sp)
        Text(text, color = fg, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Gf.errorBg)
            .border(1.dp, Gf.errorBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(Gf.errorFg),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(text, color = Gf.errorText, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    ctaText: String?,
    onCta: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // soft brand glow behind the icon tile
            Box(
                Modifier.size(220.dp).background(
                    Brush.radialGradient(
                        listOf(Gf.primary.copy(alpha = if (Gf.isDark) 0.22f else 0.12f), Color.Transparent),
                    ),
                ),
            )
            Box(
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Gf.surface)
                    .border(1.dp, Gf.outline, RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 40.dp, height = 30.dp).border(2.5.dp, Gf.primary, RoundedCornerShape(8.dp)))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(title, color = Gf.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(9.dp))
        Text(subtitle, color = Gf.onSurfaceMuted, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (ctaText != null && onCta != null) {
            Spacer(Modifier.height(24.dp))
            GfGradientButton("+  $ctaText", onCta)
        }
    }
}

@Composable
fun ErrorStateView(title: String, subtitle: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Gf.errorBg), contentAlignment = Alignment.Center) {
            Text("!", color = Gf.errorFg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = Gf.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Gf.textMuted, fontSize = 13.5.sp, lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        androidx.compose.material3.OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(13.dp)) {
            Text("↻  Retry", color = Gf.textSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}
