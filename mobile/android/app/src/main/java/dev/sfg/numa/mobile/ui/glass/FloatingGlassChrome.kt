package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen

internal val LocalGlassButtonTone = staticCompositionLocalOf { GlassTone.CONTROL }

@Composable
internal fun rememberFloatingGlassScene(): GlassScene =
    rememberGlassScene(animationTick = LocalGlassScene.current?.animationTick)

/** Capture the scrolling content and its background, excluding its floating controls. */
@Composable
internal fun GlassContentLayer(
    scene: GlassScene,
    modifier: Modifier = Modifier,
    background: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val parent = LocalGlassScene.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.glassSceneSource(scene)) {
        Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionOnScreen() }
            .drawBehind {
                if (parent?.washRecorded == true) {
                    translate(parent.washOrigin.x - origin.x, parent.washOrigin.y - origin.y) {
                        scale(parent.downscale, pivot = Offset.Zero) { drawLayer(parent.washSample) }
                    }
                }
            })
        if (background != null) Box(Modifier.matchParentSize().background(background))
        content()
    }
}

/** This scope is outside the captured content, so full-scene blur is acyclic. */
@Composable
internal fun FloatingGlassControls(scene: GlassScene, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlassScene provides scene,
        LocalGlassCapture provides GlassCapture.NONE,
        LocalGlassButtonTone provides GlassTone.CHROME,
        content = content,
    )
}
