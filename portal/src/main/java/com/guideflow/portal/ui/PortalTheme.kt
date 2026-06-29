package com.guideflow.portal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** GuideFlow portal palette, taken from the approved wireframes. */
object Gf {
    val primary = Color(0xFF4F5BD5)
    val ink = Color(0xFF11141B)
    val surface = Color(0xFFF7F8FB)
    val card = Color(0xFFFFFFFF)
    val border = Color(0xFFECEEF3)
    val borderStrong = Color(0xFFE3E5EC)

    val textPrimary = Color(0xFF11141B)
    val textSecondary = Color(0xFF5A606C)
    val textMuted = Color(0xFF8A909C)
    val textFaint = Color(0xFFB3B8C2)
    val fieldBg = Color(0xFFF4F5FB)
    val chipBg = Color(0xFFF1F2F7)

    val tooltip = Color(0xFF2563EB); val tooltipBg = Color(0xFFE7EFFE)
    val spotlight = Color(0xFFB45309); val spotlightBg = Color(0xFFFDF0E0)
    val modal = Color(0xFF7C3AED); val modalBg = Color(0xFFEFE7FE)

    val publishedFg = Color(0xFF1E7D45); val publishedBg = Color(0xFFE3F5EA)
    val draftFg = Color(0xFF8A6D1F); val draftBg = Color(0xFFFBEEC8)

    val errorFg = Color(0xFFD64545); val errorBg = Color(0xFFFDECEC)
    val errorBorder = Color(0xFFF5C2C2); val errorText = Color(0xFF9A2B2B)
    val warnBg = Color(0xFFFFF7E6); val warnBorder = Color(0xFFF4DCA0); val warnText = Color(0xFF8A6D1F)
}

@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Gf.primary,
            onPrimary = Color.White,
            background = Gf.surface,
            onBackground = Gf.textPrimary,
            surface = Gf.card,
            onSurface = Gf.textPrimary,
            surfaceVariant = Gf.fieldBg,
            outline = Gf.borderStrong,
            error = Gf.errorFg,
        ),
        content = content,
    )
}
