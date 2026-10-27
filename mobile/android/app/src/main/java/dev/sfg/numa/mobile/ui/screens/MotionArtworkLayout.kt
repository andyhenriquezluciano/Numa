package dev.sfg.numa.mobile.ui.screens

/** Short artwork reaches the controls; screen-height artwork remains edge to edge. */
internal fun motionArtworkAspect(
    sourceAspect: Float,
    screenAspect: Float,
    widthPx: Float,
    controlsTopPx: Float?,
    overlap: Float = 0.12f,
): Float {
    val natural = maxOf(sourceAspect, screenAspect)
    if (controlsTopPx == null || controlsTopPx <= 0f || widthPx <= 0f) return natural
    return (widthPx * (1f - overlap) / controlsTopPx).coerceIn(screenAspect, natural)
}
