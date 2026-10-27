package dev.sfg.numa.mobile.catalog

internal fun defaultPlaylistBrowseId(id: String): String = when (id.removePrefix("VL")) {
    "LM", "FEmusic_liked_videos" -> "VLLM"
    "SE" -> "VLSE"
    else -> id
}
