package dev.sfg.numa.mobile.model

/** Prefer the rendition composed for the visible player, preserving offline fallbacks. */
fun Track.playerMotionArtwork(square: Boolean = false): String = if (square) {
    animatedArtworkUrl.ifBlank { animatedArtworkVerticalUrl }
} else {
    animatedArtworkVerticalUrl.ifBlank { animatedArtworkUrl }
}
