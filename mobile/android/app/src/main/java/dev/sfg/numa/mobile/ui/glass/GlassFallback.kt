package dev.sfg.numa.mobile.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal val DesktopBackdropFilter by lazy {
    val matrix = ColorMatrix().apply { setSaturation(1.25f) }
    matrix.postConcat(ColorMatrix().apply { setScale(0.97f, 0.97f, 0.97f, 1f) })
    ColorMatrixColorFilter(matrix)
}

internal val SaturatedBackdropEffect by lazy {
    RenderEffect.createColorFilterEffect(DesktopBackdropFilter).asComposeRenderEffect()
}

/** PopupBloomSurface finish for Android 12, with the same neutral film and inset frame. */
internal class GlassFrostGradient(private val spec: GlassSpec) {
    private val sheen = Brush.horizontalGradient(listOf(Color(0x08FFFFFF), Color(0x14FFFFFF), Color(0x08FFFFFF)))
    fun draw(scope: DrawScope, outline: Outline, tint: Color, blurred: Boolean, currentSpec: GlassSpec = spec) = with(scope) {
        val s = currentSpec
        drawOutline(outline, (if (blurred) s.base else s.solidBase)
            .copy(alpha = if (blurred) s.film else s.solidFilm))
        drawOutline(outline, sheen)
        val topHeight = if (size.height < 80.dp.toPx()) size.height * 0.5f
            else minOf(size.height * 0.35f, 120.dp.toPx())
        drawOutline(outline, Brush.verticalGradient(listOf(Color(0x0FFFFFFF), Color.Transparent), endY = topHeight))
        if (s.borderAlpha > 0.01f) {
            listOf(0f to 0.188f, 1f to 0.10f, 2f to 0.04f).forEach { (insetDp, alpha) ->
                val inset = (insetDp + 0.5f).dp.toPx()
                val sx = (size.width - inset * 2) / size.width
                val sy = (size.height - inset * 2) / size.height
                if (sx > 0 && sy > 0) translate(inset, inset) {
                    scale(sx, sy, pivot = Offset.Zero) {
                        drawOutline(outline, Color.White.copy(alpha = alpha * s.borderAlpha), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
}

