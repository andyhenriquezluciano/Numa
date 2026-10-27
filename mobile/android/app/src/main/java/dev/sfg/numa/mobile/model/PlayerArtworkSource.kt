package dev.sfg.numa.mobile.model

/** Both player sizes use the same motion cover, including offline files and GIFs. */
fun Track.playerMotionArtwork(): String = animatedArtworkVerticalUrl.ifBlank { animatedArtworkUrl }
