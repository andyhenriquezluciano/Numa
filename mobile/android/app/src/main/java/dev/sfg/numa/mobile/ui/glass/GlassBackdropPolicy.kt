package dev.sfg.numa.mobile.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class GlassCapture { NONE, WASH, SCENE }
internal val LocalGlassCapture = staticCompositionLocalOf { GlassCapture.NONE }

/** Navigation entries may coexist during transitions, but cannot share artwork samples. */
internal fun glassArtworkMatchesOwner(sourceOwner: Any, paneOwner: Any?): Boolean = sourceOwner === paneOwner

/** A pane inside a recording must never reference that recording's output. */
internal fun glassBackdropFor(tone: GlassTone, capture: GlassCapture): GlassCapture? = when {
    capture == GlassCapture.WASH -> null
    capture == GlassCapture.SCENE -> GlassCapture.WASH
    tone == GlassTone.CHROME || tone == GlassTone.OVERLAY -> GlassCapture.SCENE
    else -> GlassCapture.WASH
}
