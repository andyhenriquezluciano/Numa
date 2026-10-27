package dev.sfg.numa.mobile.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntSize
import dev.sfg.numa.mobile.model.GlassTheme
import kotlin.math.ceil
import androidx.lifecycle.compose.LocalLifecycleOwner

internal class GlassArtworkSource(val sample: GraphicsLayer, val scale: Float, val priority: Int, val owner: Any) {
    var bounds by mutableStateOf(Rect.Zero)
    var recorded = false
    var blurRadius = Float.NaN
}

/** Record artwork alone so controls can blur it without sampling themselves. */
internal fun Modifier.glassArtworkSource(priority: Int = 10): Modifier = composed {
    val scene = LocalGlassScene.current ?: return@composed this
    if (LocalGlass.current.theme == GlassTheme.ROSE_PINE) return@composed this
    val full = rememberGraphicsLayer()
    val sample = rememberGraphicsLayer()
    val owner = LocalLifecycleOwner.current
    val source = remember(scene, sample, priority, owner) { GlassArtworkSource(sample, scene.downscale, priority, owner) }
    DisposableEffect(scene, source) {
        scene.artworkSources.add(source)
        onDispose { source.recorded = false; scene.artworkSources.remove(source) }
    }
    onGloballyPositioned {
        source.bounds = Rect(it.positionOnScreen(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }.drawWithContent {
        full.record { this@drawWithContent.drawContent() }
        drawLayer(full)
        val radius = DesktopGlass.BlurRadius.toPx() / source.scale
        if (radius != source.blurRadius) {
            sample.renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(DesktopBackdropFilter),
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
            source.blurRadius = radius
        }
        sample.record(IntSize(ceil(size.width / source.scale).toInt().coerceAtLeast(1),
            ceil(size.height / source.scale).toInt().coerceAtLeast(1))) {
            scale(1f / source.scale, pivot = Offset.Zero) { drawLayer(full) }
        }
        source.recorded = true
    }
}

internal fun DrawScope.drawGlassArtwork(scene: GlassScene, here: Offset, owner: Any?): Boolean {
    val pane = Rect(here, size)
    var drawn = false
    for (source in scene.artworkSources.sortedBy { it.priority }) {
        if (!glassArtworkMatchesOwner(source.owner, owner) || !source.recorded || !source.bounds.overlaps(pane)) continue
        translate(source.bounds.left - here.x, source.bounds.top - here.y) {
            scale(source.scale, pivot = Offset.Zero) { drawLayer(source.sample) }
        }
        drawn = true
    }
    return drawn
}
