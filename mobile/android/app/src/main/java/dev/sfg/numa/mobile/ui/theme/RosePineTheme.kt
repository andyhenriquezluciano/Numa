package dev.sfg.numa.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassTheme

internal val RosePineBorderWidth = 2.dp

// Official colour roles from https://rosepinetheme.com/palette/.
data class RosePinePalette(
    val Base: Color,
    val Surface: Color,
    val Overlay: Color,
    val Muted: Color,
    val Subtle: Color,
    val Text: Color,
    val Love: Color,
    val Gold: Color,
    val Rose: Color,
    val Pine: Color,
    val Foam: Color,
    val Iris: Color,
    val HighlightLow: Color,
    val HighlightMed: Color,
    val HighlightHigh: Color,
    val Highlight: Color,
    val AccentInk: Color,
    val OnRose: Color,
    private val light: Boolean,
) {
    val SecondaryText = if (light) lerp(Subtle, Text, 0.52f) else Subtle
    val FavoriteInk = if (light) lerp(Love, Text, 0.38f) else lerp(Love, Rose, 0.24f)
    val InfoInk = if (light) Pine else Foam
    val GoldInk = if (light) lerp(Gold, Text, 0.44f) else Gold
    val CategoryInk = if (light) lerp(Iris, Text, 0.44f) else Iris
    val InfoSurface = InfoInk.copy(alpha = 0.08f).compositeOver(Surface)
}

private val NightPalette = RosePinePalette(
    Base = Color(0xFF191724),
    Surface = Color(0xFF1F1D2E),
    Overlay = Color(0xFF26233A),
    Muted = Color(0xFF6E6A86),
    Subtle = Color(0xFF908CAA),
    Text = Color(0xFFE0DEF4),
    Love = Color(0xFFEB6F92),
    Gold = Color(0xFFF6C177),
    Rose = Color(0xFFEBBCBA),
    Pine = Color(0xFF31748F),
    Foam = Color(0xFF9CCFD8),
    Iris = Color(0xFFC4A7E7),
    HighlightLow = Color(0xFF21202E),
    HighlightMed = Color(0xFF403D52),
    HighlightHigh = Color(0xFF524F67),
    Highlight = Color(0xFF49383F),
    AccentInk = Color(0xFFEBBCBA),
    OnRose = Color(0xFF191724),
    light = false,
)

private val DawnPalette = RosePinePalette(
    Base = Color(0xFFFAF4ED),
    Surface = Color(0xFFFFFAF3),
    Overlay = Color(0xFFF2E9E1),
    Muted = Color(0xFF9893A5),
    Subtle = Color(0xFF797593),
    Text = Color(0xFF464261),
    Love = Color(0xFFB4637A),
    Gold = Color(0xFFEA9D34),
    Rose = Color(0xFFD7827E),
    Pine = Color(0xFF286983),
    Foam = Color(0xFF56949F),
    Iris = Color(0xFF907AA9),
    HighlightLow = Color(0xFFF4EDE8),
    HighlightMed = Color(0xFFDFDAD9),
    HighlightHigh = Color(0xFFCECACD),
    Highlight = Color(0xFFEFD4CF),
    AccentInk = Color(0xFF914454),
    OnRose = Color(0xFF32283A),
    light = true,
)

fun rosePinePalette(theme: GlassTheme): RosePinePalette =
    if (theme == GlassTheme.ROSE_PINE_DAWN) DawnPalette else NightPalette

@Composable
internal fun currentRosePine(): RosePinePalette? = when (LocalGlassTheme.current) {
    GlassTheme.ROSE_PINE -> NightPalette
    GlassTheme.ROSE_PINE_DAWN -> DawnPalette
    else -> null
}

val RosePineDawnColorScheme = lightColorScheme(
    primary = DawnPalette.AccentInk,
    onPrimary = DawnPalette.Surface,
    secondary = DawnPalette.InfoInk,
    onSecondary = DawnPalette.Surface,
    tertiary = DawnPalette.CategoryInk,
    onTertiary = DawnPalette.Surface,
    background = DawnPalette.Base,
    onBackground = DawnPalette.Text,
    surface = DawnPalette.Surface,
    onSurface = DawnPalette.Text,
    surfaceVariant = DawnPalette.Overlay,
    onSurfaceVariant = DawnPalette.SecondaryText,
    outline = DawnPalette.Muted,
    outlineVariant = DawnPalette.HighlightMed,
    error = DawnPalette.FavoriteInk,
    onError = DawnPalette.Surface,
)

val RosePineColorScheme = darkColorScheme(
    primary = NightPalette.Rose,
    onPrimary = NightPalette.Base,
    secondary = NightPalette.InfoInk,
    onSecondary = NightPalette.Base,
    tertiary = NightPalette.CategoryInk,
    onTertiary = NightPalette.Base,
    background = NightPalette.Base,
    onBackground = NightPalette.Text,
    surface = NightPalette.Surface,
    onSurface = NightPalette.Text,
    surfaceVariant = NightPalette.Overlay,
    onSurfaceVariant = NightPalette.SecondaryText,
    outline = NightPalette.Muted,
    outlineVariant = NightPalette.HighlightHigh,
    error = NightPalette.Love,
    onError = NightPalette.Base,
)
