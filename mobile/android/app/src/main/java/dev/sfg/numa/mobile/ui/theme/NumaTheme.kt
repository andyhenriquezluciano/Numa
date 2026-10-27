/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.sfg.numa.connect.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NumaColorScheme = darkColorScheme(
    primary = CanopyColors.Accent,
    onPrimary = Color.Black,
    secondary = CanopyColors.SecondaryAccent,
    onSecondary = Color.Black,
    tertiary = CanopyColors.Favorite,
    background = CanopyColors.Chrome,
    onBackground = CanopyColors.Text,
    surface = CanopyColors.Canvas,
    onSurface = CanopyColors.Text,
    surfaceVariant = CanopyColors.Surface,
    onSurfaceVariant = CanopyColors.Muted,
    outline = CanopyColors.Rule,
    outlineVariant = CanopyColors.RuleStrong,
    error = CanopyColors.Danger,
)

/** Modern expressive rounded corners. */
private val NumaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Expressive typography scale inspired by SimpMusic. */
private val NumaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
        color = CanopyColors.Text,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        color = CanopyColors.Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = CanopyColors.Text,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = CanopyColors.Text,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = CanopyColors.Text,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = CanopyColors.Text,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = CanopyColors.Text,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = CanopyColors.Muted,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = CanopyColors.Muted,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

/**
 * NÜMA's fixed purple accent, shared by controls and surfaces.
 */
val LocalAccent = staticCompositionLocalOf { CanopyColors.Accent }
val LocalGlassTheme = staticCompositionLocalOf { dev.sfg.numa.mobile.model.GlassTheme.CRISTAL }
val LocalGlassBorders = staticCompositionLocalOf { dev.sfg.numa.mobile.model.GlassBorders.PREDETERMINADO }

@Composable
fun NumaTheme(
    settings: dev.sfg.numa.mobile.model.NumaSettings? = null,
    content: @Composable () -> Unit,
) {
    val theme = settings?.glassTheme ?: dev.sfg.numa.mobile.model.GlassTheme.CRISTAL
    val borders = settings?.glassBorders ?: dev.sfg.numa.mobile.model.GlassBorders.PREDETERMINADO
    val accent = if (theme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) RosePineColors.Rose else CanopyColors.Accent
    val colorScheme = if (theme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) {
        RosePineColorScheme
    } else {
        NumaColorScheme.copy(primary = accent, secondary = accent)
    }

    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalGlassTheme provides theme,
        LocalGlassBorders provides borders,
        LocalContentColor provides (if (theme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) RosePineColors.Text else CanopyColors.Text),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (theme == dev.sfg.numa.mobile.model.GlassTheme.ROSE_PINE) {
                NumaTypography.copy(
                    displayLarge = NumaTypography.displayLarge.copy(color = RosePineColors.Text),
                    headlineLarge = NumaTypography.headlineLarge.copy(color = RosePineColors.Text),
                    headlineMedium = NumaTypography.headlineMedium.copy(color = RosePineColors.Text),
                    titleLarge = NumaTypography.titleLarge.copy(color = RosePineColors.Text),
                    titleMedium = NumaTypography.titleMedium.copy(color = RosePineColors.Text),
                    titleSmall = NumaTypography.titleSmall.copy(color = RosePineColors.Text),
                    bodyLarge = NumaTypography.bodyLarge.copy(color = RosePineColors.Text),
                    bodyMedium = NumaTypography.bodyMedium.copy(color = RosePineColors.Muted),
                    bodySmall = NumaTypography.bodySmall.copy(color = RosePineColors.Muted),
                    labelLarge = NumaTypography.labelLarge.copy(color = RosePineColors.Text),
                    labelMedium = NumaTypography.labelMedium.copy(color = RosePineColors.Text),
                    labelSmall = NumaTypography.labelSmall.copy(color = RosePineColors.Subtle),
                )
            } else NumaTypography,
            shapes = NumaShapes,
            content = content,
        )
    }
}

/**
 * Wallpaper-derived colours are tuned for Material's lighter surfaces and can land too dark or too
 * washed out against Numa's near-black chrome, so saturation and lightness are held to a range
 * that stays readable.
 */
fun Color.legibleOnDarkChrome(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    if (hsl[1] < 0.10f) return CanopyColors.Accent
    hsl[1] = hsl[1].coerceIn(0.45f, 0.95f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.78f)
    return Color(ColorUtils.HSLToColor(hsl))
}

