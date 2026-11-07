package dev.sfg.numa.mobile.ui.screens

internal data class MotionArtworkFrame(val left: Float, val top: Float, val width: Float, val height: Float)

/** Full-width canvas: controls overlap its faded bottom instead of shrinking it into a card. */
internal fun motionArtworkFrame(
    sourceAspect: Float,
    widthPx: Float,
    heightPx: Float,
    headerBottomPx: Float,
    controlsTopPx: Float?,
): MotionArtworkFrame {
    val width = widthPx.coerceAtLeast(0f)
    val height = heightPx.coerceAtLeast(0f)
    val aspect = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val controls = (controlsTopPx ?: height * 0.65f).coerceIn(headerBottomPx.coerceIn(0f, height), height)
    val naturalHeight = width / aspect
    val canvasHeight = maxOf(naturalHeight, controls / 0.88f).coerceAtMost(height)
    return MotionArtworkFrame(0f, 0f, width, canvasHeight)
}
