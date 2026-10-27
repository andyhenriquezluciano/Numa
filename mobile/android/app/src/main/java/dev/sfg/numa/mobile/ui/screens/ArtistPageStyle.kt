package dev.sfg.numa.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.ui.components.ArtworkPalette
import dev.sfg.numa.mobile.ui.components.KawarpArtworkBackdrop
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.glass.rememberGlassStyle
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.theme.RosePineColors
import dev.sfg.numa.mobile.ui.theme.legibleOnDarkChrome

@Composable
internal fun ArtistPageStyle(palette: ArtworkPalette, content: @Composable () -> Unit) {
    val inherited = LocalGlass.current
    val tint = animateColorAsState(palette.accent, tween(500), label = "ArtistGlassTint")
    val accent = if (inherited.theme == GlassTheme.ROSE_PINE) RosePineColors.Rose
        else palette.accent.legibleOnDarkChrome()
    CompositionLocalProvider(
        LocalGlass provides rememberGlassStyle(tint, inherited.theme, inherited.borders),
        LocalAccent provides accent,
        content = content,
    )
}

@Composable
internal fun ArtistAnimatedBackdrop(url: String, palette: ArtworkPalette) {
    val settings by NumaGraph.from(LocalContext.current).settings.settings.collectAsStateWithLifecycle()
    KawarpArtworkBackdrop(url, settings.animatedBackground, Modifier.fillMaxSize(), renderScale = 0.5f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to palette.deep.copy(alpha = 0.08f),
        0.5f to palette.deep.copy(alpha = 0.24f),
        1f to Color.Black.copy(alpha = 0.45f),
    )))
}
