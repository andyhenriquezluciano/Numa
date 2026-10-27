package dev.sfg.numa.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Rosé Pine theme palette and color scheme.
 * Minimalist, warm, and low-transparency dark palette.
 */
object RosePineColors {
    val Base = Color(0xFF191724)
    val Surface = Color(0xFF1F1D2E)
    val Overlay = Color(0xFF26233A)
    val Muted = Color(0xFF908CAA)
    val Subtle = Color(0xFF6E6A86)
    val Text = Color(0xFFE0DEF4)
    val Love = Color(0xFFEB6F92)
    val Gold = Color(0xFFF6C177)
    val Rose = Color(0xFFEBBCBA)
    val Pine = Color(0xFF31748F)
    val Foam = Color(0xFF9CCFD8)
    val Iris = Color(0xFFC4A7E7)
    val HighlightLow = Color(0xFF21202E)
    val HighlightMed = Color(0xFF403D52)
    val HighlightHigh = Color(0xFF524F67)
}

val RosePineColorScheme = darkColorScheme(
    primary = RosePineColors.Rose,
    onPrimary = RosePineColors.Base,
    secondary = RosePineColors.Foam,
    onSecondary = RosePineColors.Base,
    tertiary = RosePineColors.Love,
    background = RosePineColors.Base,
    onBackground = RosePineColors.Text,
    surface = RosePineColors.Surface,
    onSurface = RosePineColors.Text,
    surfaceVariant = RosePineColors.Overlay,
    onSurfaceVariant = RosePineColors.Muted,
    outline = RosePineColors.Overlay,
    outlineVariant = RosePineColors.HighlightHigh,
    error = RosePineColors.Love,
)
